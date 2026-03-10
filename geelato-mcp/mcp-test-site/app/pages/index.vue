   <template>
  <div>
    <!-- 概览统计 -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
      <div class="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-gray-500 text-sm">MCP 服务总数</p>
            <p class="text-3xl font-bold text-blue-600">{{ services.length }}</p>
          </div>
          <div class="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
            <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"/>
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-gray-500 text-sm">工具总数</p>
            <p class="text-3xl font-bold text-indigo-600">{{ totalTools }}</p>
          </div>
          <div class="w-12 h-12 bg-indigo-100 rounded-lg flex items-center justify-center">
            <svg class="w-6 h-6 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-gray-500 text-sm">测试用例总数</p>
            <p class="text-3xl font-bold text-green-600">{{ totalTestCases }}</p>
          </div>
          <div class="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
            <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"/>
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl shadow-sm p-6 border border-gray-100">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-gray-500 text-sm">测试通过率</p>
            <p class="text-3xl font-bold" :class="passRate >= 90 ? 'text-green-600' : passRate >= 70 ? 'text-yellow-600' : 'text-red-600'">
              {{ passRate }}%
            </p>
          </div>
          <div class="w-12 h-12 rounded-lg flex items-center justify-center" :class="passRate >= 90 ? 'bg-green-100' : passRate >= 70 ? 'bg-yellow-100' : 'bg-red-100'">
            <svg class="w-6 h-6" :class="passRate >= 90 ? 'text-green-600' : passRate >= 70 ? 'text-yellow-600' : 'text-red-600'" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/>
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- 服务配置 -->
    <div class="bg-white rounded-xl shadow-sm p-6 border border-gray-100 mb-6">
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-bold text-gray-800">MCP 服务配置</h3>
        <div class="flex items-center space-x-4">
          <div class="flex items-center space-x-2">
            <span class="text-sm text-gray-500">服务地址:</span>
            <input 
              v-model="mcpConfig.baseUrl" 
              type="text" 
              class="px-3 py-1.5 text-sm border border-gray-300 rounded-lg w-48"
              placeholder="http://localhost:8081"
            >
          </div>
          <div class="flex items-center space-x-2">
            <span class="text-sm text-gray-500">API Key:</span>
            <input 
              v-model="mcpConfig.apiKey" 
              type="password" 
              class="px-3 py-1.5 text-sm border border-gray-300 rounded-lg w-48"
              placeholder="输入 API Key"
            >
          </div>
          <button
            @click="testConnection"
            :disabled="isTestingConnection"
            class="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {{ isTestingConnection ? '测试中...' : '测试连接' }}
          </button>
        </div>
      </div>
      <div v-if="connectionStatus" class="mt-3 flex items-center space-x-2" :class="connectionStatus.success ? 'text-green-600' : 'text-red-600'">
        <span class="w-2 h-2 rounded-full" :class="connectionStatus.success ? 'bg-green-500' : 'bg-red-500'"></span>
        <span class="text-sm">{{ connectionStatus.message }}</span>
      </div>
    </div>

    <!-- 服务列表 -->
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h2 class="text-xl font-bold text-gray-800">MCP 服务列表</h2>
        <div class="flex items-center space-x-3">
          <button
            @click="expandAllResults"
            class="px-3 py-1.5 text-sm bg-white border border-gray-300 rounded-lg hover:bg-gray-50 flex items-center space-x-1"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
            </svg>
            <span>全部展开</span>
          </button>
          <button
            @click="collapseAllResults"
            class="px-3 py-1.5 text-sm bg-white border border-gray-300 rounded-lg hover:bg-gray-50 flex items-center space-x-1"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"/>
            </svg>
            <span>全部折叠</span>
          </button>
          <button
            @click="runAllTests"
            :disabled="isRunningAll"
            class="px-4 py-2 bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-lg hover:from-blue-700 hover:to-indigo-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
          >
            <svg v-if="isRunningAll" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>{{ isRunningAll ? '执行中...' : '运行全部测试' }}</span>
          </button>
        </div>
      </div>

      <div v-for="service in services" :key="service.id" class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <!-- 服务头部 -->
        <div class="p-6 border-b border-gray-100 bg-gradient-to-r from-gray-50 to-white">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-4">
              <div class="w-12 h-12 rounded-xl flex items-center justify-center" :class="service.iconBg">
                <svg class="w-6 h-6" :class="service.iconColor" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path v-if="service.icon === 'platform'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"/>
                  <path v-else-if="service.icon === 'logistics'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"/>
                  <path v-else-if="service.icon === 'order'" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
                </svg>
              </div>
              <div>
                <h3 class="text-lg font-bold text-gray-800">{{ service.name }}</h3>
                <p class="text-gray-500 text-sm">{{ service.description }}</p>
              </div>
            </div>
            <div class="flex items-center space-x-4">
              <div class="text-right">
                <p class="text-sm text-gray-500">工具数量</p>
                <p class="text-xl font-bold text-gray-800">{{ service.tools.length }}</p>
              </div>
              <button
                @click="toggleService(service.id)"
                class="p-2 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <svg
                  class="w-5 h-5 text-gray-500 transition-transform"
                  :class="{ 'rotate-180': expandedServices.includes(service.id) }"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- 工具列表 -->
        <div v-show="expandedServices.includes(service.id)" class="divide-y divide-gray-100">
          <div v-for="tool in service.tools" :key="tool.name" class="p-6 hover:bg-gray-50 transition-colors">
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="flex items-center space-x-3 mb-2">
                  <h4 class="font-semibold text-gray-800">{{ tool.name }}</h4>
                  <span class="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">{{ tool.className }}</span>
                </div>
                <p class="text-gray-600 text-sm mb-4">{{ tool.description }}</p>

                <!-- 测试用例列表 -->
                <div class="space-y-3">
                  <div
                    v-for="testCase in tool.testCases"
                    :key="testCase.id"
                    class="border border-gray-200 rounded-lg overflow-hidden"
                    :class="{
                      'bg-gray-50': testCase.status === 'pending',
                      'bg-yellow-50 border-yellow-200': testCase.status === 'running',
                      'bg-green-50 border-green-200': testCase.status === 'passed',
                      'bg-red-50 border-red-200': testCase.status === 'failed'
                    }"
                  >
                    <!-- 测试用例头部 -->
                    <div class="p-3 flex items-center justify-between">
                      <div class="flex items-center space-x-3">
                        <span
                          class="w-2 h-2 rounded-full"
                          :class="{
                            'bg-gray-300': testCase.status === 'pending',
                            'bg-yellow-400': testCase.status === 'running',
                            'bg-green-500': testCase.status === 'passed',
                            'bg-red-500': testCase.status === 'failed'
                          }"
                        />
                        <div>
                          <div class="flex items-center space-x-2">
                            <p class="text-sm font-medium text-gray-700">{{ testCase.name }}</p>
                            <span class="px-1.5 py-0.5 bg-blue-100 text-blue-700 text-xs rounded font-mono">{{ testCase.methodName }}</span>
                          </div>
                          <p class="text-xs text-gray-500 mt-0.5">参数: {{ JSON.stringify(testCase.params) }}</p>
                        </div>
                      </div>
                      <div class="flex items-center space-x-3">
                        <span
                          v-if="testCase.duration"
                          class="text-xs text-gray-500"
                        >
                          {{ testCase.duration }}ms
                        </span>
                        <button
                          v-if="testCase.result"
                          @click="toggleResult(testCase.id)"
                          class="p-1 hover:bg-gray-200 rounded transition-colors"
                          :class="{ 'rotate-180': expandedResults.includes(testCase.id) }"
                        >
                          <svg class="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                          </svg>
                        </button>
                        <button
                          @click="runTest(service.id, tool.name, testCase)"
                          :disabled="testCase.status === 'running'"
                          class="px-3 py-1 text-sm bg-white border rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                          :class="{
                            'border-gray-300': testCase.status === 'pending',
                            'border-yellow-300 text-yellow-700': testCase.status === 'running',
                            'border-green-300 text-green-700': testCase.status === 'passed',
                            'border-red-300 text-red-700': testCase.status === 'failed'
                          }"
                        >
                          {{ testCase.status === 'running' ? '执行中' : testCase.status === 'pending' ? '运行' : '重新运行' }}
                        </button>
                      </div>
                    </div>

                    <!-- 测试结果 -->
                    <div 
                      v-if="testCase.result" 
                      v-show="expandedResults.includes(testCase.id)"
                      class="border-t px-3 py-2" 
                      :class="testCase.result.success ? 'border-green-200 bg-green-100/50' : 'border-red-200 bg-red-100/50'"
                    >
                      <div class="flex items-center space-x-2 mb-1">
                        <span
                          class="px-2 py-0.5 text-xs rounded font-medium"
                          :class="testCase.result.success ? 'bg-green-200 text-green-800' : 'bg-red-200 text-red-800'"
                        >
                          {{ testCase.result.success ? '成功' : '失败' }}
                        </span>
                        <span class="text-xs text-gray-500">{{ testCase.result.timestamp }}</span>
                      </div>
                      <pre class="text-xs overflow-x-auto" :class="testCase.result.success ? 'text-green-900' : 'text-red-900'">{{ testCase.result.data }}</pre>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

// MCP 服务配置
const mcpConfig = ref({
  baseUrl: 'http://localhost:8081',
  apiKey: 'test-api-key-123456'
})

const connectionStatus = ref(null)
const isTestingConnection = ref(false)

// MCP 服务数据
const services = ref([
  {
    id: 'platform',
    name: 'MCP Platform 服务',
    description: '平台核心服务，提供系统信息、用户查询、数据字典、页面配置、视图查询、元数据模型等功能',
    icon: 'platform',
    iconBg: 'bg-blue-100',
    iconColor: 'text-blue-600',
    tools: [
      {
        name: 'SystemInfoTool',
        className: 'SystemInfoTool',
        description: '系统信息工具 - 提供系统运行状态和信息查询功能',
        testCases: [
          { id: 'sys-1', name: '获取系统基本信息', methodName: 'getSystemInfo', params: {}, status: 'pending', result: null },
          { id: 'sys-2', name: '获取 JVM 内存使用情况', methodName: 'getMemoryInfo', params: {}, status: 'pending', result: null },
          { id: 'sys-3', name: '获取 CPU 信息', methodName: 'getCpuInfo', params: {}, status: 'pending', result: null }
        ]
      },
      {
        name: 'UserQueryTool',
        className: 'UserQueryTool',
        description: '用户查询工具 - 提供用户和权限相关的查询功能',
        testCases: [
          { id: 'user-1', name: '获取所有用户列表', methodName: 'listAllUsers', params: {}, status: 'pending', result: null },
          { id: 'user-2', name: '根据用户 ID 查询用户信息', methodName: 'getUserById', params: { userId: '6652990717893414912' }, status: 'pending', result: null },
          { id: 'user-3', name: '根据用户名查询用户信息', methodName: 'getUserByUsername', params: { username: 'admin' }, status: 'pending', result: null },
          { id: 'user-4', name: '获取所有角色列表', methodName: 'listAllRoles', params: {}, status: 'pending', result: null },
          { id: 'user-5', name: '获取用户角色', methodName: 'getUserRoles', params: { userId: '6652990717893414912' }, status: 'pending', result: null }
        ]
      },
      {
        name: 'DictQueryTool',
        className: 'DictQueryTool',
        description: '数据字典查询工具 - 提供数据字典相关的查询功能',
        testCases: [
          { id: 'dict-1', name: '获取所有字典类型', methodName: 'listAllDictTypes', params: {}, status: 'pending', result: null },
          { id: 'dict-2', name: '获取多语言类型字典项', methodName: 'getDictItems', params: { dictCode: 'multiLangType' }, status: 'pending', result: null },
          { id: 'dict-3', name: '获取订单状态字典项', methodName: 'getDictItems', params: { dictCode: 'order_status' }, status: 'pending', result: null },
          { id: 'dict-4', name: '检查字典是否存在', methodName: 'checkDictExists', params: { dictCode: 'multiLangType' }, status: 'pending', result: null }
        ]
      },
      {
        name: 'PageConfigTool',
        className: 'PageConfigTool',
        description: '页面配置工具 - 提供页面配置相关的查询功能',
        testCases: [
          { id: 'page-1', name: '获取所有页面配置', methodName: 'listAllPages', params: {}, status: 'pending', result: null },
          { id: 'page-2', name: '获取页面配置详情', methodName: 'getPageConfig', params: { pageId: '9223136890012700672' }, status: 'pending', result: null },
          { id: 'page-3', name: '获取实体关联页面', methodName: 'getPagesByEntity', params: { entityName: 'platform_user' }, status: 'pending', result: null }
        ]
      },
      {
        name: 'ViewQueryTool',
        className: 'ViewQueryTool',
        description: '视图查询工具 - 提供视图元数据相关的查询功能',
        testCases: [
          { id: 'view-1', name: '获取所有视图名称', methodName: 'listAllViewNames', params: {}, status: 'pending', result: null },
          { id: 'view-2', name: '获取视图元数据', methodName: 'getViewMeta', params: { viewName: 'v_demo_entity' }, status: 'pending', result: null },
          { id: 'view-3', name: '获取实体关联视图', methodName: 'getViewsByEntity', params: { entityName: 'platform_user' }, status: 'pending', result: null }
        ]
      },
      {
        name: 'MetaModelTool',
        className: 'MetaModelTool',
        description: '元数据模型工具 - 提供实体模型元数据相关的查询功能',
        testCases: [
          { id: 'meta-1', name: '获取所有实体名称', methodName: 'listAllEntityNames', params: {}, status: 'pending', result: null },
          { id: 'meta-2', name: '获取所有实体精简信息', methodName: 'listAllEntityLiteMetas', params: {}, status: 'pending', result: null },
          { id: 'meta-3', name: '获取实体元数据', methodName: 'getEntityMeta', params: { entityName: 'platform_user' }, status: 'pending', result: null },
          { id: 'meta-4', name: '检查实体是否存在', methodName: 'checkEntityExists', params: { entityName: 'platform_user' }, status: 'pending', result: null },
          { id: 'meta-5', name: '获取元数据统计', methodName: 'getMetaStatistics', params: {}, status: 'pending', result: null }
        ]
      }
    ]
  },
  {
    id: 'logistics',
    name: 'MCP Logistics 服务',
    description: '物流相关服务，提供集装箱查询、货代查询、运输轨迹等功能',
    icon: 'logistics',
    iconBg: 'bg-green-100',
    iconColor: 'text-green-600',
    tools: [
      {
        name: 'ContainerQueryTool',
        className: 'ContainerQueryTool',
        description: '集装箱查询工具 - 提供集装箱位置、货代列表、运输轨迹查询',
        testCases: [
          { id: 'container-1', name: '查询集装箱位置', methodName: 'queryContainerLocation', params: { containerNo: 'COSU1234567' }, status: 'pending', result: null },
          { id: 'container-2', name: '查询货代集装箱列表', methodName: 'queryFreightContainers', params: { freightId: 'F001' }, status: 'pending', result: null },
          { id: 'container-3', name: '查询集装箱运输轨迹', methodName: 'queryContainerTrack', params: { containerNo: 'COSU1234567' }, status: 'pending', result: null }
        ]
      }
    ]
  },
  {
    id: 'order',
    name: 'MCP Order 服务',
    description: '订单相关服务，提供订单查询、用户订单、订单统计等功能',
    icon: 'order',
    iconBg: 'bg-purple-100',
    iconColor: 'text-purple-600',
    tools: [
      {
        name: 'OrderQueryTool',
        className: 'OrderQueryTool',
        description: '订单查询工具 - 提供订单信息、用户订单列表、订单统计查询',
        testCases: [
          { id: 'order-1', name: '根据订单号查询订单', methodName: 'queryOrderByNo', params: { orderNo: 'ORD20250101001' }, status: 'pending', result: null },
          { id: 'order-2', name: '查询用户订单列表', methodName: 'queryUserOrders', params: { userId: 'U001' }, status: 'pending', result: null },
          { id: 'order-3', name: '查询订单统计信息', methodName: 'queryOrderStatistics', params: { dateRange: '2025-01' }, status: 'pending', result: null }
        ]
      }
    ]
  }
])

const expandedServices = ref(['platform'])
const expandedResults = ref([])
const isRunningAll = ref(false)

const totalTools = computed(() => {
  return services.value.reduce((sum, s) => sum + s.tools.length, 0)
})

const totalTestCases = computed(() => {
  return services.value.reduce((sum, s) => {
    return sum + s.tools.reduce((toolSum, t) => toolSum + t.testCases.length, 0)
  }, 0)
})

const passRate = computed(() => {
  let total = 0
  let passed = 0
  services.value.forEach(s => {
    s.tools.forEach(t => {
      t.testCases.forEach(tc => {
        if (tc.status !== 'pending') {
          total++
          if (tc.status === 'passed') passed++
        }
      })
    })
  })
  return total > 0 ? Math.round((passed / total) * 100) : 0
})

// 测试 MCP 连接
async function testConnection() {
  isTestingConnection.value = true
  connectionStatus.value = null

  try {
    const response = await fetch(`${mcpConfig.value.baseUrl}/api/mcp/tools`, {
      headers: {
        'X-API-Key': mcpConfig.value.apiKey
      }
    })

    if (response.ok) {
      connectionStatus.value = {
        success: true,
        message: '连接成功！MCP 服务运行正常'
      }
    } else {
      connectionStatus.value = {
        success: false,
        message: `连接失败: HTTP ${response.status}`
      }
    }
  } catch (error) {
    connectionStatus.value = {
      success: false,
      message: `连接失败: ${error.message}`
    }
  }

  isTestingConnection.value = false
}

function toggleService(id) {
  const index = expandedServices.value.indexOf(id)
  if (index > -1) {
    expandedServices.value.splice(index, 1)
  } else {
    expandedServices.value.push(id)
  }
}

function toggleResult(testCaseId) {
  const index = expandedResults.value.indexOf(testCaseId)
  if (index > -1) {
    expandedResults.value.splice(index, 1)
  } else {
    expandedResults.value.push(testCaseId)
  }
}

function expandAllResults() {
  expandedResults.value = []
  services.value.forEach(s => {
    s.tools.forEach(t => {
      t.testCases.forEach(tc => {
        if (tc.result) {
          expandedResults.value.push(tc.id)
        }
      })
    })
  })
}

function collapseAllResults() {
  expandedResults.value = []
}

// 调用真实的 MCP 工具
async function callMcpTool(toolName, methodName, params) {
  const requestBody = {
    tool: toolName,
    method: methodName,
    params: params
  }

  const response = await fetch(`${mcpConfig.value.baseUrl}/api/mcp/tool/call`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': mcpConfig.value.apiKey
    },
    body: JSON.stringify(requestBody)
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`HTTP ${response.status}: ${errorText}`)
  }

  return await response.json()
}

async function runTest(serviceId, toolName, testCase) {
  testCase.status = 'running'
  testCase.result = null
  if (!expandedResults.value.includes(testCase.id)) {
    expandedResults.value.push(testCase.id)
  }
  const startTime = Date.now()

  try {
    // 调用真实的 MCP 工具
    const result = await callMcpTool(toolName, testCase.methodName, testCase.params)

    const duration = Date.now() - startTime
    testCase.duration = duration

    // 检查业务逻辑是否成功（data.success 为 true 表示成功）
    const businessSuccess = result.data && result.data.success === true

    if (businessSuccess) {
      testCase.status = 'passed'
      testCase.result = {
        success: true,
        timestamp: new Date().toLocaleString(),
        data: JSON.stringify(result, null, 2)
      }
      // 执行成功后自动折叠
      const index = expandedResults.value.indexOf(testCase.id)
      if (index > -1) {
        expandedResults.value.splice(index, 1)
      }
    } else {
      // 业务逻辑失败
      testCase.status = 'failed'
      testCase.result = {
        success: false,
        timestamp: new Date().toLocaleString(),
        data: JSON.stringify(result, null, 2)
      }
      // 失败后保持展开
    }
  } catch (error) {
    const duration = Date.now() - startTime
    testCase.duration = duration
    testCase.status = 'failed'

    testCase.result = {
      success: false,
      timestamp: new Date().toLocaleString(),
      data: JSON.stringify({
        code: 500,
        message: '调用失败',
        error: error.message,
        tool: toolName,
        method: testCase.methodName
      }, null, 2)
    }
    // 失败后保持展开
  }
}

async function runAllTests() {
  isRunningAll.value = true

  for (const service of services.value) {
    if (!expandedServices.value.includes(service.id)) {
      expandedServices.value.push(service.id)
    }

    for (const tool of service.tools) {
      for (const testCase of tool.testCases) {
        await runTest(service.id, tool.name, testCase)
      }
    }
  }

  isRunningAll.value = false
}
</script>
