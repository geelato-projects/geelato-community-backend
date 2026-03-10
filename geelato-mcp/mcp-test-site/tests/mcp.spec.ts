import { test, expect, Page } from '@playwright/test';

const MCP_BASE_URL = process.env.MCP_BASE_URL || 'http://localhost:8081';
const TEST_SITE_URL = process.env.TEST_SITE_URL || 'http://localhost:3000';
const API_KEY = process.env.API_KEY || 'test-api-key-123456';

test.describe('MCP 测试站点', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(TEST_SITE_URL);
    await page.waitForLoadState('networkidle');
  });

  test('页面应该正确加载', async ({ page }) => {
    await expect(page.locator('text=MCP 服务总数')).toBeVisible();
    await expect(page.locator('text=工具总数')).toBeVisible();
    await expect(page.locator('text=测试用例总数')).toBeVisible();
    await expect(page.locator('text=测试通过率')).toBeVisible();
  });

  test('应该显示服务列表', async ({ page }) => {
    await expect(page.locator('text=MCP Platform 服务')).toBeVisible();
    await expect(page.locator('text=MCP Logistics 服务')).toBeVisible();
    await expect(page.locator('text=MCP Order 服务')).toBeVisible();
  });

  test('应该显示工具数量统计', async ({ page }) => {
    const totalTools = page.locator('text=工具总数').locator('..').locator('p.text-3xl');
    await expect(totalTools).toBeVisible();
    const toolsCount = await totalTools.textContent();
    expect(parseInt(toolsCount || '0')).toBeGreaterThan(0);
  });

  test('服务配置区域应该可见', async ({ page }) => {
    await expect(page.locator('text=MCP 服务配置')).toBeVisible();
    await expect(page.locator('input[placeholder*="localhost"]')).toBeVisible();
    await expect(page.locator('input[placeholder="输入 API Key"]')).toBeVisible();
    await expect(page.locator('button:has-text("测试连接")')).toBeVisible();
  });

  test('测试连接按钮应该可以点击', async ({ page }) => {
    const testButton = page.locator('button:has-text("测试连接")');
    await expect(testButton).toBeEnabled();
    await testButton.click();
    await page.waitForTimeout(2000);
  });

  test('展开/折叠服务应该正常工作', async ({ page }) => {
    // 找到包含"MCP Platform 服务"文本的元素，然后找到其父级服务卡片
    const serviceHeader = page.locator('h3:has-text("MCP Platform 服务")');
    await expect(serviceHeader).toBeVisible();
    
    // 向上查找到服务卡片容器，然后找到展开/折叠按钮
    const toggleButton = serviceHeader.locator('xpath=ancestor::div[contains(@class, "bg-gradient-to-r")]//button').last();
    await expect(toggleButton).toBeVisible();
    await toggleButton.click();
    await page.waitForTimeout(500);
    
    await toggleButton.click();
    await page.waitForTimeout(500);
  });

  test('运行全部测试按钮应该可见', async ({ page }) => {
    const runAllButton = page.locator('button:has-text("运行全部测试")');
    await expect(runAllButton).toBeVisible();
    await expect(runAllButton).toBeEnabled();
  });
});

test.describe('MCP API 测试', () => {
  test('获取工具列表', async ({ request }) => {
    const response = await request.get(`${MCP_BASE_URL}/api/mcp/tools`, {
      headers: {
        'X-API-Key': API_KEY
      }
    });
    
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.code).toBe(200);
    expect(data.data).toBeDefined();
    expect(data.data.SystemInfoTool).toBeDefined();
    expect(data.data.UserQueryTool).toBeDefined();
  });

  test('调用 SystemInfoTool - getSystemInfo', async ({ request }) => {
    const response = await request.post(`${MCP_BASE_URL}/api/mcp/tool/call`, {
      headers: {
        'X-API-Key': API_KEY,
        'Content-Type': 'application/json'
      },
      data: {
        tool: 'SystemInfoTool',
        method: 'getSystemInfo',
        params: {}
      }
    });
    
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.code).toBe(200);
    expect(data.data).toBeDefined();
  });

  test('调用 UserQueryTool - listAllUsers', async ({ request }) => {
    const response = await request.post(`${MCP_BASE_URL}/api/mcp/tool/call`, {
      headers: {
        'X-API-Key': API_KEY,
        'Content-Type': 'application/json'
      },
      data: {
        tool: 'UserQueryTool',
        method: 'listAllUsers',
        params: {}
      }
    });
    
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.code).toBe(200);
  });

  test('调用 DictQueryTool - listAllDictTypes', async ({ request }) => {
    const response = await request.post(`${MCP_BASE_URL}/api/mcp/tool/call`, {
      headers: {
        'X-API-Key': API_KEY,
        'Content-Type': 'application/json'
      },
      data: {
        tool: 'DictQueryTool',
        method: 'listAllDictTypes',
        params: {}
      }
    });
    
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.code).toBe(200);
  });

  test('调用 PageConfigTool - listAllPages', async ({ request }) => {
    const response = await request.post(`${MCP_BASE_URL}/api/mcp/tool/call`, {
      headers: {
        'X-API-Key': API_KEY,
        'Content-Type': 'application/json'
      },
      data: {
        tool: 'PageConfigTool',
        method: 'listAllPages',
        params: {}
      }
    });
    
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.code).toBe(200);
  });

  test('调用 MetaModelTool - listAllEntityNames', async ({ request }) => {
    const response = await request.post(`${MCP_BASE_URL}/api/mcp/tool/call`, {
      headers: {
        'X-API-Key': API_KEY,
        'Content-Type': 'application/json'
      },
      data: {
        tool: 'MetaModelTool',
        method: 'listAllEntityNames',
        params: {}
      }
    });
    
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data.code).toBe(200);
  });

  test('无效 API Key 应该返回 401', async ({ request }) => {
    const response = await request.get(`${MCP_BASE_URL}/api/mcp/tools`, {
      headers: {
        'X-API-Key': 'invalid-key'
      }
    });
    
    expect(response.status()).toBe(401);
  });
});

test.describe('MCP 工具完整测试', () => {
  const tools = [
    {
      name: 'SystemInfoTool',
      methods: [
        { name: 'getSystemInfo', params: {} },
        { name: 'getMemoryInfo', params: {} },
        { name: 'getCpuInfo', params: {} }
      ]
    },
    {
      name: 'UserQueryTool',
      methods: [
        { name: 'listAllUsers', params: {} },
        { name: 'getUserById', params: { userId: '6652990717893414912' } },
        { name: 'getUserByUsername', params: { username: 'admin' } },
        { name: 'listAllRoles', params: {} }
      ]
    },
    {
      name: 'DictQueryTool',
      methods: [
        { name: 'listAllDictTypes', params: {} },
        { name: 'getDictItems', params: { dictCode: 'multiLangType' } },
        { name: 'checkDictExists', params: { dictCode: 'multiLangType' } }
      ]
    },
    {
      name: 'PageConfigTool',
      methods: [
        { name: 'listAllPages', params: {} },
        { name: 'getPageConfig', params: { pageId: '9223136890012700672' } },
        { name: 'getPagesByEntity', params: { entityName: 'platform_user' } }
      ]
    },
    {
      name: 'ViewQueryTool',
      methods: [
        { name: 'listAllViewNames', params: {} },
        { name: 'getViewMeta', params: { viewName: 'v_demo_entity' } }
      ]
    },
    {
      name: 'MetaModelTool',
      methods: [
        { name: 'listAllEntityNames', params: {} },
        { name: 'listAllEntityLiteMetas', params: {} },
        { name: 'getEntityMeta', params: { entityName: 'platform_user' } },
        { name: 'getEntityFields', params: { entityName: 'platform_user' } },
        { name: 'checkEntityExists', params: { entityName: 'platform_user' } },
        { name: 'getMetaStatistics', params: {} }
      ]
    }
  ];

  for (const tool of tools) {
    for (const method of tool.methods) {
      test(`${tool.name}.${method.name}`, async ({ request }) => {
        const response = await request.post(`${MCP_BASE_URL}/api/mcp/tool/call`, {
          headers: {
            'X-API-Key': API_KEY,
            'Content-Type': 'application/json'
          },
          data: {
            tool: tool.name,
            method: method.name,
            params: method.params
          }
        });
        
        expect(response.ok()).toBeTruthy();
        const data = await response.json();
        expect(data.code).toBe(200);
      });
    }
  }
});
