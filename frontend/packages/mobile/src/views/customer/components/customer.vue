<template>
  <div class="flex h-full flex-col overflow-hidden">
    <div class="flex items-center gap-[12px] bg-[var(--text-n10)] p-[8px_16px]">
      <van-button
        v-permission="['CUSTOMER_MANAGEMENT:ADD']"
        plain
        icon="plus"
        type="primary"
        size="small"
        @click="goCreate"
      >
      </van-button>
      <van-search
        v-model="keyword"
        shape="round"
        :placeholder="t('customer.searchPlaceholder')"
        class="crm-search"
        @search="search"
        @clear="search"
      />
    </div>
    <div class="filter-buttons">
      <van-button
        v-for="item of tabList"
        :key="item.name"
        round
        size="small"
        class="!border-none !px-[16px] !py-[4px] !text-[14px]"
        :class="
          activeFilter === item.name
            ? '!bg-[var(--primary-7)] !text-[var(--primary-8)]'
            : '!bg-[var(--text-n9)] !text-[var(--text-n1)]'
        "
        @click="activeFilter = item.name"
      >
        {{ item.tab }}
      </van-button>
    </div>
    <van-tabs
      v-model:active="activeCustomFilter"
      class="custom-filter-tabs"
      background="var(--text-n10)"
      line-width="20px"
      @click-tab="handleCustomFilterTab"
    >
      <van-tab v-for="item of customFilterOptions" :key="item.value" :name="item.value" :title="item.label" />
    </van-tabs>
    <CrmList
      ref="crmListRef"
      :keyword="keyword"
      :list-params="listParams"
      class="p-[16px]"
      :item-gap="16"
      :close-init-load="!activeFilter"
      :load-list-api="getCustomerList"
      :transform="transformFormData"
    >
      <template #item="{ item }">
        <CrmListCommonItem
          :item="item"
          :actions="getListItemActions(item.collaborationType)"
          name-key="ownerName"
          @click="goDetail"
        ></CrmListCommonItem>
      </template>
    </CrmList>
  </div>
</template>

<script setup lang="ts">
  import { useRouter } from 'vue-router';
  import { closeToast, showConfirmDialog, showLoadingToast, showSuccessToast } from 'vant';

  import { CustomerSearchTypeEnum } from '@lib/shared/enums/customerEnum';
  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { CollaborationType } from '@lib/shared/models/customer';

  import CrmList from '@/components/pure/crm-list/index.vue';
  import CrmListCommonItem from '@/components/pure/crm-list-common-item/index.vue';

  import { deleteCustomer, getCustomerList } from '@/api/modules';
  import useFormCreateTransform from '@/hooks/useFormCreateTransform';
  import useHiddenTab from '@/hooks/useHiddenTab';

  import { ClueRouteEnum, CommonRouteEnum, CustomerRouteEnum } from '@/enums/routeEnum';
  import type { FormCreateFieldOption } from '@cordys/web/src/components/business/crm-form-create/types';
  import { ReasonTypeEnum } from '@lib/shared/enums/moduleEnum';

  const { t } = useI18n();
  const router = useRouter();

  const crmListRef = ref<InstanceType<typeof CrmList>>();
  const customFilterOptions = ref<FormCreateFieldOption[]>([]);
  const keyword = ref('');
  const customFilters = ref<any[]>([]);
  const activeCustomFilter = ref('ALL');
  const filterButtons = [
    {
      name: CustomerSearchTypeEnum.ALL,
      tab: t('customer.all'),
    },
    {
      name: CustomerSearchTypeEnum.SELF,
      tab: t('customer.mine'),
    },
    {
      name: CustomerSearchTypeEnum.DEPARTMENT,
      tab: t('customer.deptCustomer'),
    },
    {
      name: CustomerSearchTypeEnum.CUSTOMER_COLLABORATION,
      tab: t('customer.cooperationCustomer'),
    },
  ];
  const { tabList, activeFilter } = useHiddenTab(filterButtons, FormDesignKeyEnum.CUSTOMER);
  const listParams = computed(() => {
    return {
      viewId: activeFilter.value,
      keyword: keyword.value,
      filters: customFilters.value,
    };
  });

  const { fieldList, transformFormData } = await useFormCreateTransform(FormDesignKeyEnum.CUSTOMER);

  const customFilter = fieldList.value.find((item) => item.id === '176658417185600000');
  if (customFilter) {
    customFilterOptions.value = customFilter.options as FormCreateFieldOption[];
    // 添加到第一个筛选项
    customFilterOptions.value.unshift({
      value: 'ALL',
      label: '全部',
    });
  }

  const actions = [
    {
      key: 'edit',
      label: t('common.edit'),
      icon: 'iconicon_handwritten_signature',
      permission: ['CUSTOMER_MANAGEMENT:UPDATE'],
      action: (item: any) => {
        router.push({
          name: CommonRouteEnum.FORM_CREATE,
          query: {
            id: item.id,
            formKey: FormDesignKeyEnum.CUSTOMER,
            needInitDetail: 'Y',
          },
        });
      },
    },
    {
      key: 'recycle',
      label: t('customer.moveToOpenSea'),
      icon: 'iconicon_jump',
      permission: ['CUSTOMER_MANAGEMENT:RECYCLE'],
      action: (item: any) => {
        router.push({
          name: ClueRouteEnum.MOVE_TO_POOL,
          query: {
            id: item.id,
            name: item.value,
            reasonKey: ReasonTypeEnum.CUSTOMER_POOL_RS,
          },
        });
      },
    },
    {
      key: 'writeRecord',
      label: t('common.writeRecord'),
      icon: 'iconicon_edit1',
      permission: ['CUSTOMER_MANAGEMENT:UPDATE'],
      action: (item: any) => {
        router.push({
          name: CommonRouteEnum.FORM_CREATE,
          query: {
            id: item.id,
            formKey: FormDesignKeyEnum.FOLLOW_RECORD_CUSTOMER,
            initialSourceName: item.name,
          },
        });
      },
    },
    {
      key: 'delete',
      label: t('common.delete'),
      icon: 'iconicon_delete',
      permission: ['CUSTOMER_MANAGEMENT:DELETE'],
      action: (item: any) => {
        showConfirmDialog({
          title: t('customer.deleteTitle'),
          message: t('customer.deleteTip'),
          confirmButtonText: t('common.confirmDelete'),
          confirmButtonColor: 'var(--error-red)',
          beforeClose: async (action) => {
            if (action === 'confirm') {
              try {
                await deleteCustomer(item.id);
                showSuccessToast(t('common.deleteSuccess'));
                crmListRef.value?.loadList(true);
                return Promise.resolve(true);
              } catch (error) {
                // eslint-disable-next-line no-console
                console.log(error);
                return Promise.resolve(false);
              }
            } else {
              return Promise.resolve(true);
            }
          },
        });
      },
    },
  ];

  function getListItemActions(collaborationType: CollaborationType) {
    if (activeFilter.value === CustomerSearchTypeEnum.CUSTOMER_COLLABORATION) {
      return collaborationType === 'COLLABORATION' ? actions.filter((item) => item.key === 'writeRecord') : [];
    }
    return actions;
  }

  watch(
    () => activeFilter.value,
    () => {
      nextTick(() => {
        crmListRef.value?.loadList(true);
      });
    }
  );

  onActivated(() => {
    crmListRef.value?.loadList(true);
  });

  async function search() {
    nextTick(async () => {
      showLoadingToast(t('common.searching'));
      await crmListRef.value?.loadList(true);
      closeToast();
    });
  }

  function goCreate() {
    router.push({
      name: CommonRouteEnum.FORM_CREATE,
      query: {
        formKey: FormDesignKeyEnum.CUSTOMER,
      },
    });
  }

  function goDetail(item: any) {
    router.push({
      name: CustomerRouteEnum.CUSTOMER_DETAIL,
      query: {
        id: item.id,
        name: item.name,
        needInitDetail: 'Y',
      },
    });
  }

  function handleCustomFilterTab({ name }: { name: string }) {
    if (name === 'ALL') {
      // 点击'全部'按钮时，清空筛选器
      customFilters.value = [];
    } else {
      customFilters.value = [
        {
          name: '176658417185600000',
          value: [name],
          multipleValue: false,
          operator: 'IN',
          type: 'SELECT',
        },
      ];
    }
    // 手动触发列表刷新
    nextTick(() => {
      crmListRef.value?.loadList(true);
    });
  }
</script>

<style lang="less" scoped>
  .filter-buttons {
    @apply flex;

    gap: 8px;
    padding: 8px 4px;
    background-color: var(--text-n10);
    .half-px-border-bottom();
  }
  .custom-filter-tabs {
    :deep(.van-tabs__wrap) {
      .half-px-border-bottom();
    }
    :deep(.van-tabs__nav) {
      padding-bottom: 4px;
      background-color: var(--text-n10);
    }
    :deep(.van-tab) {
      flex: 0 0 auto;
      padding: 0 16px;
      padding-bottom: 8px;
      font-size: 14px;
      line-height: 20px;
    }
    :deep(.van-tabs__line) {
      background-color: var(--primary-8);
    }
  }
</style>
