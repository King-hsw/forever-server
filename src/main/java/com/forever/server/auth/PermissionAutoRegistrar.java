package com.forever.server.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动时扫描全部 {@link Perm} 声明，幂等补 {@code sys_permission} 行并授予内置 ADMIN 角色，
 * 权限显示名/模块自动取自注解 → Swagger 注解 → 权限码推导，新接口无需手工插入权限数据。
 * <p>
 * 已存在的行（含手工种子）一律不覆盖、不删除。
 * ponytail: 只扫容器 bean 及其公有方法上的 @Perm；别处引用的权限码需手工入库。
 */
@Slf4j
@Component
public class PermissionAutoRegistrar implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final RbacMapper rbacMapper;

    public PermissionAutoRegistrar(ApplicationContext applicationContext, RbacMapper rbacMapper) {
        this.applicationContext = applicationContext;
        this.rbacMapper = rbacMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        // code -> [name, module]；同一 code 多处声明时首个生效
        Map<String, String[]> perms = new LinkedHashMap<>();
        for (Object bean : applicationContext.getBeansOfType(Object.class).values()) {
            Class<?> clazz = ClassUtils.getUserClass(bean.getClass());
            Tag tag = clazz.getAnnotation(Tag.class);
            collect(clazz.getAnnotation(Perm.class), null, tag, perms);
            for (Method method : clazz.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                collect(method.getAnnotation(Perm.class), method, tag, perms);
            }
        }
        int inserted = 0;
        for (Map.Entry<String, String[]> e : perms.entrySet()) {
            inserted += rbacMapper.insertPermissionIfAbsent(e.getKey(), e.getValue()[0], e.getValue()[1]);
            rbacMapper.grantAdminByCode(e.getKey());
        }
        log.info("permission auto-register: {} points, {} new rows inserted, ADMIN role granted", perms.size(), inserted);
    }

    private void collect(Perm perm, Method method, Tag tag, Map<String, String[]> out) {
        if (perm == null || perm.value().isEmpty() || out.containsKey(perm.value())) {
            return;
        }
        String name = perm.name();
        if (name.isEmpty() && method != null) {
            Operation op = method.getAnnotation(Operation.class);
            if (op != null && !op.summary().isEmpty()) {
                name = op.summary();
            }
        }
        if (name.isEmpty()) {
            name = perm.value();
        }
        String module = perm.module();
        if (module.isEmpty() && tag != null) {
            module = tag.name();
        }
        int i = perm.value().indexOf(':');
        if (module.isEmpty()) {
            module = i > 0 ? perm.value().substring(0, i) : "其他";
        }
        out.put(perm.value(), new String[]{name, module});
    }
}
