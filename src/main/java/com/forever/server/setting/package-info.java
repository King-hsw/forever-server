/**
 * 站点设置模块：运行参数的持久化与后台实时调整。
 * sys_site_config KV 表 + 内存缓存，管理端 API 修改即时生效；
 * application.yml 中的同名配置仅作为初始默认值。
 */
package com.forever.server.setting;
