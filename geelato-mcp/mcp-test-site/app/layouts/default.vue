<template>
  <div class="min-h-screen bg-gray-50">
    <header class="bg-gradient-to-r from-blue-600 to-indigo-700 text-white shadow-lg">
      <div class="container mx-auto px-4 py-6">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <div class="w-10 h-10 bg-white/20 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"/>
              </svg>
            </div>
            <div>
              <h1 class="text-2xl font-bold">Geelato MCP 测试平台</h1>
              <p class="text-blue-100 text-sm">模型上下文协议服务测试与监控</p>
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <span class="px-3 py-1 bg-white/20 rounded-full text-sm">
              服务状态: <span class="text-green-300 font-semibold">● 运行中</span>
            </span>
          </div>
        </div>
      </div>
    </header>

    <main class="container mx-auto px-4 py-8">
      <slot />
    </main>

    <footer class="bg-gray-800 text-gray-400 py-6 mt-12">
      <div class="container mx-auto px-4 text-center">
        <p>Geelato MCP Platform © 2025 - 基于 Nuxt 4.x 构建</p>
      </div>
    </footer>
  </div>
</template>
