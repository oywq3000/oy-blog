# admin-service 后端一期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 admin-service（博客后台管理服务，BFF 聚合架构）并打通文章管理、评论审核、用户管理、统计看板四大 MVP 模块及 ADMIN 鉴权全链路。

**Architecture:** admin-service 是管理端统一入口，通过 Feign 调用 article-service / user-service 的既有与新增管理接口（透传管理员身份头，不依赖 X-Service-Call 信任）；统计看板只读直连同库统计表。ADMIN 角色链路三层：网关拦截 → admin-service 自身 Security → @RequirePermission 注解拦截器（放 common，三个服务共用）。下游管理接口同样挂 @RequirePermission，直接 HTTP 访问也会被拦截。

**Tech Stack:** Spring Boot 3.4.11 / Spring Cloud Alibaba / MyBatis Plus 3.5.7 / OpenFeign + Sentinel / MySQL / Redis / JUnit 5 + Mockito

**Spec:** `docs/superpowers/specs/2026-08-26-blog-admin-service-design.md`（计划以此为准，执行者需同时阅读）

## Global Constraints

- JDK 21：命令行构建前必须 `export JAVA_HOME=/d/DevelopKit/jdk-21.0.8`（默认 JAVA_HOME 是 JDK 20，会编译失败）
- 单测只跑指定类时加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则无匹配测试类会报错
- `src/test` 目录被 `.gitignore` 忽略：**测试代码只写不提交**，每个任务最后只 `git add` main 源码
- i18n 资源全部在 `oy-blog-common/src/main/resources/i18n/`（messages.properties + messages_en_US.properties），一期无需新增 key（复用 error.forbidden / error.unavailable / common.success 等）
- 响应统一 `Result<T>`；分页统一 `PageVo(currentPage, pageSize, total, totalPages, data)`，用 MP `Page` + `selectPage` 直接 `new PageVo<>(...)`（项目无 PageUtils）
- 主键用 `UUIDUtils.getId()`；逻辑删除用 `@TableLogic`（User 实体用 `idDeleted` 字段，新表字段命名为 `is_deleted`）
- 测试约定（照抄现有 ArticleStatsBizServiceImplTest）：`@ExtendWith(MockitoExtension.class)`；`@BeforeEach` 里反射注入 `I18nUtils.messageSource` 假 MessageSource（`lenient().when(...)`）；service 用 `spy(real)` + 匿名子类覆盖 `getUserId()`
- 提交信息风格：`feat: 中文描述`，正文末加 `Co-Authored-By: Claude <noreply@anthropic.com>`
- 所有新增管理 Controller 方法必须挂 `@RequirePermission("admin:xxx")`（拦截器读取 `X-User-Type` 头校验 ADMIN，网关保证该头不可伪造）

---

### Task 1: admin-service 模块骨架 + 公共权限注解

**Files:**
- Create: `oy-blog-common/src/main/java/com/oyproj/common/security/annotation/RequirePermission.java`
- Create: `oy-blog-common/src/main/java/com/oyproj/common/security/interceptor/RequirePermissionInterceptor.java`
- Modify: `oy-blog-common/pom.xml`（加 spring-boot-starter-test，test scope）
- Create: `oy-blog-service/admin-service/pom.xml`
- Modify: `oy-blog-service/pom.xml`（`<modules>` 加 `<module>admin-service</module>`）
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/AdminApplication.java`
- Create: `oy-blog-service/admin-service/src/main/resources/bootstrap.yml`
- Create: `oy-blog-service/admin-service/src/main/resources/bootstrap-dev.yml`
- Create: `oy-blog-service/admin-service/src/main/resources/application.yml`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/config/AdminSecurityConfig.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/base/AdminBizBase.java`
- Test: `oy-blog-common/src/test/java/com/oyproj/common/security/interceptor/RequirePermissionInterceptorTest.java`

**Interfaces:**
- Produces: `@RequirePermission(String value)` 注解；`RequirePermissionInterceptor`（@Aspect，读取请求头 X-User-Type 校验 ADMIN）；`AdminBizBase`（`getCurrentUserId()` / `getCurrentUserDTO()`）；`AdminSecurityConfig`（`/public/**` 要求已认证、其余要求 SecurityContext 主体 blogRole=ADMIN）

- [ ] **Step 1: 写失败测试**（common 模块）

```java
package com.oyproj.common.security.interceptor;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.security.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequirePermissionInterceptorTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private final RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void requestWithUserType(String userType) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstant.USER_TYPE.getValue(), userType);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RequirePermission permission() {
        return new RequirePermission() {
            @Override
            public String value() {
                return "admin:test";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RequirePermission.class;
            }
        };
    }

    @Test
    void adminHeader_passes() throws Throwable {
        requestWithUserType(BlogRole.ADMIN.name());
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = interceptor.check(joinPoint, permission());

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    void readerHeader_throwsForbidden() {
        requestWithUserType(BlogRole.READER.name());

        assertThrows(ForbiddenException.class, () -> interceptor.check(joinPoint, permission()));
        verify(joinPoint, never()).proceed();
    }

    @Test
    void noRequestAttributes_throwsForbidden() {
        assertThrows(ForbiddenException.class, () -> interceptor.check(joinPoint, permission()));
        verify(joinPoint, never()).proceed();
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-common test -Dtest=RequirePermissionInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（RequirePermissionInterceptor 不存在）

- [ ] **Step 3: 写实现**

`oy-blog-common/src/main/java/com/oyproj/common/security/annotation/RequirePermission.java`:

```java
package com.oyproj.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端权限注解。
 * MVP 实现：仅校验请求头 X-User-Type = ADMIN（网关保证该头不可伪造）；
 * 预留：value 为权限码常量（如 admin:article:write），将来接入 Permission/RolePermission 表做细粒度校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /** 权限码，如 admin:article:write */
    String value();
}
```

`oy-blog-common/src/main/java/com/oyproj/common/security/interceptor/RequirePermissionInterceptor.java`:

```java
package com.oyproj.common.security.interceptor;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.security.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限拦截器：校验请求头 X-User-Type 是否为 ADMIN。
 * 为什么读请求头而不是 SecurityContext：
 * 下游服务被 Feign 调用时 X-Service-Call=true 会短路 AuthFilter 身份构建，
 * 而网关在每次转发时都会用 Redis 中的真实角色覆盖 X-User-Type 头，直接读头是各场景下都可靠且最简单的判断。
 */
@Aspect
@Component
public class RequirePermissionInterceptor {

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String userType = attrs.getRequest().getHeader(HeaderConstant.USER_TYPE.getValue());
            if (BlogRole.ADMIN.name().equals(userType)) {
                return joinPoint.proceed();
            }
        }
        throw new ForbiddenException();
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS（3 个用例全绿）

- [ ] **Step 5: 建 admin-service 模块**

`oy-blog-service/admin-service/pom.xml`（照抄 user-service 的 pom，删除 mail/thymeleaf 依赖，其余一致——parent 是 `oy-blog-service`，依赖：starter-web、starter-test、service-api、mybatis-plus-spring-boot3-starter、mysql-connector-j、springdoc、oy-blog-common、starter-security、nacos-discovery、nacos-config、loadbalancer、starter-bootstrap；build 里 spring-boot-maven-plugin）：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.oyproj</groupId>
        <artifactId>oy-blog-service</artifactId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>admin-service</artifactId>
    <packaging>jar</packaging>

    <name>admin-service</name>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.oyproj</groupId>
            <artifactId>service-api</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>com.oyproj</groupId>
            <artifactId>oy-blog-common</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`AdminApplication.java`（排除 common 的 SecurityConfig 组件扫描，由本服务 AdminSecurityConfig 接管）：

```java
package com.oyproj;

import com.oyproj.common.security.config.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.oyproj",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@EnableTransactionManagement
@EnableFeignClients(basePackages = "com.oyproj.api")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
```

`bootstrap.yml`（照抄 user-service，application.name 改 admin-service）:

```yaml
spring:
  profiles:
    active: dev
  application:
    name: admin-service
```

`bootstrap-dev.yml`（照抄 user-service）:

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: http://${NACOS_HOST:192.168.200.130}:8848
      config:
        server-addr: http://${NACOS_HOST:192.168.200.130}:8848
        file-extension: yaml
```

`application.yml`（照抄 user-service 去掉 mail/app 段，端口 8095）:

```yaml
server:
  port: 8095

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:192.168.200.130}:3306/oyblog?useUnicode=true&characterEncoding=UTF-8&serverTimezone=GMT%2B8&allowMultiQueries=true
    username: root
    password: root
  data:
    redis:
      host: ${REDIS_HOST:192.168.200.130}
      timeout: 10000
      client-type: lettuce
      lettuce:
        pool:
          max-active: 8
          max-wait: -1
          max-idle: 8
          min-idle: 0
  cloud:
    sentinel:
      enabled: true
      eager: true
      transport:
        dashboard: ${SENTINEL_HOST:192.168.200.130}:8858
        port: 8719
      filter:
        url-patterns: /**

logging:
  level:
    com.oyproj: DEBUG
    org.springframework: WARN
  file:
    path: ./logs

feign:
  sentinel:
    enabled: true
```

`AdminSecurityConfig.java`（复制 common SecurityConfig 结构 + 路径规则）:

```java
package com.oyproj.config;

import com.oyproj.common.base.Result;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.security.domain.SecurityUser;
import com.oyproj.common.security.filter.AuthFilter;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * admin-service 安全配置：/public/** 仅要求已认证（公告游客可读、通知仅读自己），其余一律要求 ADMIN。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final CommonCache commonCache;

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .anonymous(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/public/**").authenticated()
                        .anyRequest().access((authentication, context) ->
                                authentication != null
                                        && authentication.getPrincipal() instanceof SecurityUser su
                                        && su.getUser() != null
                                        && su.getUser().getBlogRole() == BlogRole.ADMIN))
                .exceptionHandling(exception -> {
                    exception.authenticationEntryPoint((request, response, authException) -> {
                        UnAuthorizedException e = new UnAuthorizedException();
                        response.setStatus(e.getErrCode());
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(JsonUtil.toJson(Result.error(e.getErrCode(), e.getMessage())));
                    }).accessDeniedHandler((request, response, accessDeniedException) -> {
                        ForbiddenException e = new ForbiddenException();
                        response.setStatus(e.getErrCode());
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(JsonUtil.toJson(Result.error(e.getErrCode(), e.getMessage())));
                    });
                });
        http.addFilterBefore(new AuthFilter(commonCache), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

`AdminBizBase.java`（照抄 UserBizBase 的 getCurrentUserId/getCurrentUserDTO）:

```java
package com.oyproj.base;

import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.security.domain.SecurityUser;
import com.oyproj.common.service.base.BaseBiz;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * admin-service 业务基类
 */
public class AdminBizBase extends BaseBiz {

    //获得当前用户id
    public String getCurrentUserId() {
        SecurityUser securityUser = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return securityUser.getUsername();
    }

    //获得当前用户
    public UserDTO getCurrentUserDTO() {
        return ((SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
    }
}
```

- [ ] **Step 6: 编译全模块**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-common,oy-blog-service/admin-service -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add oy-blog-common/src/main/java/com/oyproj/common/security/annotation/RequirePermission.java \
        oy-blog-common/src/main/java/com/oyproj/common/security/interceptor/RequirePermissionInterceptor.java \
        oy-blog-common/pom.xml \
        oy-blog-service/admin-service/ oy-blog-service/pom.xml
git commit -m "feat: admin-service 模块骨架与公共权限注解

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 网关路由 + ADMIN 角色拦截 + 真实角色注入

**Files:**
- Modify: `oy-blog-gateway/pom.xml`（加 spring-boot-starter-test，test scope）
- Modify: `oy-blog-gateway/src/main/resources/application.yml`（加 admin-service 路由 + 白名单 `/admin-service/public/**`）
- Modify: `oy-blog-gateway/src/main/java/com/oyproj/filter/AuthenticationFilter.java`（注入真实角色 + ADMIN 路径拦截）
- Test: `oy-blog-gateway/src/test/java/com/oyproj/filter/AuthenticationFilterTest.java`

**Interfaces:**
- Produces: 网关对所有 /admin-service/**（除 /public/**）要求 Redis 中用户角色为 ADMIN；X-User-Type 头按 Redis 中 UserDTO.blogRole 真实注入（不再硬编码 READER）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.filter;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.JwtUtil;
import com.oyproj.properties.AuthProperties;
import com.oyproj.utils.GuestUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 注意：网关过滤器会调用 request.mutate()/exchange.mutate()，
 * 而 MockServerHttpRequest 等 mock 实现不支持 mutate，因此 request/exchange 全部用 Mockito mock。
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private CommonCache commonCache;
    @Mock
    private GatewayFilterChain chain;
    @Mock
    private ServerWebExchange exchange;
    @Mock
    private ServerWebExchange mutatedExchange;
    @Mock
    private ServerWebExchange.Builder exchangeBuilder;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpRequest mutatedRequest;
    @Mock
    private ServerHttpRequest.Builder requestBuilder;

    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        props.setWhitelist(List.of("/admin-service/public/**"));
        filter = new AuthenticationFilter(props, commonCache);
    }

    private void stubExchange(String path) {
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(mock(ServerHttpResponse.class));
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080" + path));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer fake-token");
        when(request.getHeaders()).thenReturn(headers);
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(mutatedExchange.getRequest()).thenReturn(mutatedRequest);
    }

    private MockedStatic<JwtUtil> mockJwt(String userId) {
        MockedStatic<JwtUtil> jwt = mockStatic(JwtUtil.class);
        Claims claims = mock(Claims.class);
        jwt.when(() -> JwtUtil.parseToken(anyString())).thenReturn(claims);
        jwt.when(() -> JwtUtil.getTokenType(claims)).thenReturn(JwtUtil.TOKEN_TYPE_ACCESS);
        when(claims.getSubject()).thenReturn(userId);
        return jwt;
    }

    @Test
    void adminPath_adminUser_injectsAdminHeaders() {
        stubExchange("/admin-service/article/page");
        UserDTO admin = new UserDTO("u1", 1, BlogRole.ADMIN);
        when(commonCache.hasKey(CachePrefix.USER_ID.getPrefix() + "u1")).thenReturn(true);
        when(commonCache.get(CachePrefix.USER_ID.getPrefix() + "u1")).thenReturn(admin);
        when(chain.filter(any())).thenReturn(Mono.empty());

        try (MockedStatic<JwtUtil> jwt = mockJwt("u1")) {
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        }

        verify(requestBuilder).header(HeaderConstant.USER_ID.getValue(), "u1");
        verify(requestBuilder).header(HeaderConstant.USER_TYPE.getValue(), "ADMIN");
    }

    @Test
    void adminPath_readerUser_rejectedWithForbidden() {
        stubExchange("/admin-service/article/page");
        UserDTO reader = new UserDTO("u2", 1, BlogRole.READER);
        when(commonCache.hasKey(CachePrefix.USER_ID.getPrefix() + "u2")).thenReturn(true);
        when(commonCache.get(CachePrefix.USER_ID.getPrefix() + "u2")).thenReturn(reader);

        try (MockedStatic<JwtUtil> jwt = mockJwt("u2")) {
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ForbiddenException.class)
                    .verify();
        }
        verify(chain, never()).filter(any());
    }

    @Test
    void publicPath_invalidToken_guestPasses() {
        stubExchange("/admin-service/public/announcement");
        when(commonCache.hasKey(anyString())).thenReturn(false);
        when(chain.filter(any())).thenReturn(Mono.empty());

        try (MockedStatic<JwtUtil> jwt = mockJwt("u3");
             MockedStatic<GuestUtil> guest = mockStatic(GuestUtil.class)) {
            // token 无效 → 白名单路径按游客放行，绝不拒绝
            guest.when(() -> GuestUtil.getGuestIdFromCookie(any())).thenReturn("guest-1");
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-gateway test -Dtest=AuthenticationFilterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（spring-boot-starter-test 未引入）

- [ ] **Step 3: 改 gateway pom + application.yml**

pom 的 `<dependencies>` 内、junit 3.8.1 依赖之前加：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

application.yml：routes 列表最后加 admin-service 路由；`auth.whitelist` 最后加一行：

```yaml
        - id: admin-service
          uri: lb://admin-service
          predicates:
            - Path=/admin-service/**
          filters:
            - StripPrefix=1
```

```yaml
    - /admin-service/public/**
```

- [ ] **Step 4: 改 AuthenticationFilter**

新增 import：`com.oyproj.common.domain.dto.UserDTO`、`com.oyproj.common.exception.ForbiddenException`。

`filter()` 方法中"用户认证成功"分支（原 `return handleAuthenticatedUser(exchange, chain, authResult);` 处）改为：

```java
        if(authResult.isAuthenticated()){
            //用户认证成功
            log.debug("认证用户访问: {}, 用户ID: {}", path, authResult.getUserId());
            // 管理端路径：仅 ADMIN 可访问（/public/** 除外，如公告展示/通知读取）
            if (isAdminPath(path)) {
                UserDTO cached = (UserDTO) commonCache.get(CachePrefix.USER_ID.getPrefix() + authResult.getUserId());
                if (cached == null || cached.getBlogRole() != BlogRole.ADMIN) {
                    log.warn("非管理员访问管理端路径被拒绝: {}, 用户ID: {}", path, authResult.getUserId());
                    return Mono.error(new ForbiddenException(I18nUtils.tLocale("error.forbidden", locale)));
                }
            }
            return handleAuthenticatedUser(exchange, chain, authResult);
        }
```

新增私有方法：

```java
    /** 是否管理端受保护路径（/admin-service/** 且不在 public 白名单语义内） */
    private boolean isAdminPath(String path) {
        return path.startsWith("/admin-service/") && !path.startsWith("/admin-service/public/");
    }
```

`handleAuthenticatedUser` 方法整体替换为（从 Redis 取真实角色注入，不再硬编码 READER）：

```java
    /**
     * 处理认证用户请求：从缓存读取完整 UserDTO，注入真实角色（READER/ADMIN）
     */
    private Mono<Void> handleAuthenticatedUser(ServerWebExchange exchange,
                                               GatewayFilterChain chain,
                                               AuthenticationResult authResult) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = authResult.getUserId();
        UserDTO cached = (UserDTO) commonCache.get(CachePrefix.USER_ID.getPrefix() + userId);
        BlogRole role = (cached != null && cached.getBlogRole() != null) ? cached.getBlogRole() : BlogRole.READER;
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(HeaderConstant.USER_ID.getValue(), userId)
                .header(HeaderConstant.USER_TYPE.getValue(), role.name())
                .build();
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
```

- [ ] **Step 5: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS（3 个用例全绿）

- [ ] **Step 6: Commit**

```bash
git add oy-blog-gateway/pom.xml oy-blog-gateway/src/main/resources/application.yml \
        oy-blog-gateway/src/main/java/com/oyproj/filter/AuthenticationFilter.java
git commit -m "feat: 网关注入真实角色并拦截非 ADMIN 访问管理端

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: user-service 登录缓存真实角色 + 管理员种子 SQL

**Files:**
- Modify: `oy-blog-service/user-service/src/main/java/com/oyproj/service/impl/UserAuthBizServiceImpl.java`（login 第 71 行、refresh 第 165 行两处硬编码 READER）
- Create: `doc/sql/admin_seed.sql`（role 种子 + 给博主授 ADMIN）
- Test: `oy-blog-service/user-service/src/test/java/com/oyproj/service/impl/UserAuthBizServiceImplTest.java`

**Interfaces:**
- Produces: 登录/刷新时 UserDTO.blogRole 取真实角色（拥有 code=ADMIN 角色 → ADMIN，否则 READER）；Redis 会话缓存携带真实角色

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.common.utils.IpUtils;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.LoginDto;
import com.oyproj.domain.dto.TokenInfo;
import com.oyproj.domain.entity.Role;
import com.oyproj.domain.entity.User;
import com.oyproj.utils.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthBizServiceImplTest {

    @Mock
    private UserDao userDao;
    @Mock
    private CommonCache commonCache;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserAuthBizServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        MessageSource mockMsg = mock(MessageSource.class);
        lenient().when(mockMsg.getMessage(anyString(), any(), any())).thenReturn("OK");
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockMsg);
        service = new UserAuthBizServiceImpl(passwordEncoder, userDao, commonCache);
    }

    private User user() {
        User user = new User();
        user.setId("u1");
        user.setUsername("admin");
        user.setPassword("hashed");
        user.setStatus(1);
        user.setIdDeleted(0);
        return user;
    }

    private LoginDto loginDto(String username, String password) {
        LoginDto dto = new LoginDto();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    @Test
    void login_adminRoleUser_cachesUserDTOWithAdminRole() {
        when(userDao.getUserByName("admin")).thenReturn(user());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        Role adminRole = new Role();
        adminRole.setId("role-admin");
        adminRole.setCode("ADMIN");
        when(userDao.listRolesByUserId("u1")).thenReturn(List.of(adminRole));
        TokenInfo tokenInfo = mock(TokenInfo.class);

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            ip.when(() -> IpUtils.getClientIp(any())).thenReturn("127.0.0.1");
            security.when(SecurityUtil::getTokenInfo).thenReturn(tokenInfo);
            // SecurityUtil.login 是 void 静态方法，mockStatic 后默认 no-op
            service.login(loginDto("admin", "pw"));
        }

        verify(commonCache).put(eq(CachePrefix.USER_ID.getPrefix() + "u1"), captor.capture(), any());
        assertEquals(BlogRole.ADMIN, captor.getValue().getBlogRole());
    }

    @Test
    void login_readerUser_cachesUserDTOWithReaderRole() {
        when(userDao.getUserByName("admin")).thenReturn(user());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userDao.listRolesByUserId("u1")).thenReturn(List.of());
        TokenInfo tokenInfo = mock(TokenInfo.class);

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            ip.when(() -> IpUtils.getClientIp(any())).thenReturn("127.0.0.1");
            security.when(SecurityUtil::getTokenInfo).thenReturn(tokenInfo);
            service.login(loginDto("admin", "pw"));
        }

        verify(commonCache).put(eq(CachePrefix.USER_ID.getPrefix() + "u1"), captor.capture(), any());
        assertEquals(BlogRole.READER, captor.getValue().getBlogRole());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/user-service test -Dtest=UserAuthBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 失败（缓存的 blogRole 是 READER 而非 ADMIN）

- [ ] **Step 3: 改 login / refresh**

`UserAuthBizServiceImpl` 中第 71 行 `userDTO.setBlogRole(BlogRole.READER);` 改为：

```java
        userDTO.setBlogRole(resolveBlogRole(user.getId()));
```

第 165 行附近 refresh 重建 session 处同样替换为：

```java
            userDTO.setBlogRole(resolveBlogRole(userId));
```

类内新增私有方法：

```java
    /** 按用户角色表解析真实角色：拥有 ADMIN 角色 → ADMIN，否则 READER */
    private BlogRole resolveBlogRole(String userId) {
        List<Role> roles = userDao.listRolesByUserId(userId);
        boolean admin = roles.stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
        return admin ? BlogRole.ADMIN : BlogRole.READER;
    }
```

（需补充 import：`com.oyproj.domain.entity.Role`、`java.util.List`。）

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: 写种子 SQL**

`doc/sql/admin_seed.sql`:

```sql
-- 管理员角色与授权种子（幂等，可重复执行）
-- 用法：在部署 admin-service 前于 oyblog 库执行；将下方博主的用户名/ID 替换为实际账号

-- 1. 确保 ADMIN 角色存在
INSERT INTO `role` (id, code, name, description, created_at, updated_at)
SELECT 'seed-role-admin', 'ADMIN', '管理员', '博客管理员', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE code = 'ADMIN');

-- 2. 给博主授予 ADMIN 角色（替换 'oywq3000' 为实际用户名）
INSERT INTO `user_role` (id, user_id, role_id, created_at)
SELECT UUID(), u.id, r.id, NOW()
FROM `user` u
JOIN `role` r ON r.code = 'ADMIN'
WHERE u.username = 'oywq3000'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
```

（注：`user_role.id` 是 UUID32 字符串主键；若 `role.id`/`user.id` 实际为 UUID32 格式则上面 INSERT 的 'seed-role-admin' 需改用 32 位 UUID，执行前以库里现有 id 格式为准微调。）

- [ ] **Step 6: Commit**

```bash
git add oy-blog-service/user-service/src/main/java/com/oyproj/service/impl/UserAuthBizServiceImpl.java doc/sql/admin_seed.sql
git commit -m "feat: 登录缓存真实角色并新增管理员种子 SQL

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: admin-service 当前用户探活接口（登录链路验证点）

**Files:**
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminProfileController.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminProfileBizService.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminProfileBizServiceImpl.java`
- Test: `oy-blog-service/admin-service/src/test/java/com/oyproj/service/impl/AdminProfileBizServiceImplTest.java`

**Interfaces:**
- Produces: `GET /admin/current-user` → `Result<UserDTO>`（当前登录管理员信息，供管理前端登录后拉取并验证角色）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.security.domain.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AdminProfileBizServiceImplTest {

    private final AdminProfileBizServiceImpl service = new AdminProfileBizServiceImpl();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUser_returnsAdminDTO() {
        UserDTO admin = new UserDTO("u1", 1, BlogRole.ADMIN);
        admin.setUsername("admin");
        SecurityUser su = new SecurityUser(admin, new ArrayList<>());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(su, null, su.getAuthorities()));

        var result = service.currentUser();

        assertTrue(result.getIsSuccess());
        assertEquals("u1", result.getData().getId());
        assertEquals(BlogRole.ADMIN, result.getData().getBlogRole());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/admin-service test -Dtest=AdminProfileBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 写实现**

`service/AdminProfileBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;

/**
 * 管理员个人信息业务
 */
public interface AdminProfileBizService {

    /** 获取当前登录管理员信息 */
    Result<UserDTO> currentUser();
}
```

`service/impl/AdminProfileBizServiceImpl.java`:

```java
package com.oyproj.service.impl;

import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.service.AdminProfileBizService;
import org.springframework.stereotype.Service;

@Service
public class AdminProfileBizServiceImpl extends AdminBizBase implements AdminProfileBizService {

    @Override
    public Result<UserDTO> currentUser() {
        return Result.ok(getCurrentUserDTO());
    }
}
```

`controller/AdminProfileController.java`:

```java
package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminProfileBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员信息控制器
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileBizService biz;

    @GetMapping("/current-user")
    @RequirePermission("admin:base")
    public Result<UserDTO> currentUser() {
        return biz.currentUser();
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: 手工验证登录链路**（需本地 Nacos/MySQL/Redis 可用，见 doc/local-dev-environment.md；若 dev 环境不可达则记录到任务备注，等部署环境可用后补验）

```bash
# 1. 先在 dev 库执行 doc/sql/admin_seed.sql 给博主授 ADMIN
# 2. 起 user-service、gateway、admin-service
# 3. 登录拿 token
curl -X POST http://localhost:8080/user-service/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"oywq3000","password":"你的密码"}'
# 4. 用返回的 token 调探活接口
curl http://localhost:8080/admin-service/admin/current-user \
  -H "Authorization: Bearer <token>"
# 期望：isSuccess=true 且 data.blogRole=ADMIN
```

- [ ] **Step 6: Commit**

```bash
git add oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminProfileController.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/
git commit -m "feat: admin-service 当前用户探活接口

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: service-api 管理 Feign 客户端与跨服务 DTO

**Files:**
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/ArticleSaveDto.java`（镜像 article-service 的 ArticleSaveDto：id/title/summary/contentMd/contentHtml/coverUrl/tags/allowComment）
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/ArticleAdminPageDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/vo/ArticleAdminItemVo.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/TagSaveDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/vo/TagAdminVo.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/SeriesSaveDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/vo/SeriesAdminVo.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/CommentAdminPageDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/vo/CommentAdminItemVo.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/domain/dto/CommentAuditDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/user/domain/dto/UserAdminPageDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/user/domain/vo/UserAdminItemVo.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/user/domain/dto/UserRoleAssignDto.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/config/AdminFeignConfig.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/client/AdminArticleClient.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/article/client/fallback/AdminArticleClientFallbackFactory.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/user/client/AdminUserClient.java`
- Create: `oy-blog-service/service-api/src/main/java/com/oyproj/api/user/client/fallback/AdminUserClientFallbackFactory.java`
- Test: `oy-blog-service/service-api/src/test/java/com/oyproj/api/fallback/AdminClientsFallbackTest.java`

**Interfaces:**
- Produces（后续任务依赖的精确签名）:
  - `AdminArticleClient`：`adminArticlePage(ArticleAdminPageDto)`、`saveDraft(ArticleSaveDto)`、`publish(ArticleSaveDto)`、`deleteArticle(String)`、`saveTag(TagSaveDto)`、`deleteTag(String)`、`listTags()`、`saveSeries(SeriesSaveDto)`、`deleteSeries(String)`、`listSeries()`、`adminCommentPage(CommentAdminPageDto)`、`auditComment(CommentAuditDto)`、`deleteComment(String)`、`pinComment(String, Integer)`
  - `AdminUserClient`：`adminUserPage(UserAdminPageDto)`、`banUser(String)`、`unbanUser(String)`、`assignRole(UserRoleAssignDto)`
  - 所有方法返回 `Result<...>`；分页返回 `Result<PageVo<List<...>>>`
  - `AdminFeignConfig`（非 @Configuration！只被指定 client 引用）：RequestInterceptor 透传 X-User-Id/X-User-Type 头

- [ ] **Step 1: 写失败测试**（Fallback 工厂可独立测试）

```java
package com.oyproj.api.fallback;

import com.oyproj.api.article.client.fallback.AdminArticleClientFallbackFactory;
import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.user.client.fallback.AdminUserClientFallbackFactory;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class AdminClientsFallbackTest {

    @BeforeEach
    void setUp() throws Exception {
        // service-api 测试同样需要 I18nUtils.messageSource，注入假实现
        Field field = com.oyproj.common.utils.I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, new org.springframework.context.support.StaticMessageSource());
    }

    @Test
    void adminArticleFallback_returnsErrorResult() {
        var client = new AdminArticleClientFallbackFactory().create(new RuntimeException("down"));
        assertFalse(client.adminArticlePage(new ArticleAdminPageDto()).getIsSuccess());
        assertFalse(client.auditComment(new CommentAuditDto()).getIsSuccess());
        assertFalse(client.saveDraft(null).getIsSuccess());
    }

    @Test
    void adminUserFallback_returnsErrorResult() {
        var client = new AdminUserClientFallbackFactory().create(new RuntimeException("down"));
        assertFalse(client.adminUserPage(new UserAdminPageDto()).getIsSuccess());
        assertFalse(client.banUser("u1").getIsSuccess());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/service-api test -Dtest=AdminClientsFallbackTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（FallbackFactory 不存在）

- [ ] **Step 3: 写 DTO/VO**（全部 @Data，字段如下）

```java
// ArticleAdminPageDto
package com.oyproj.api.article.domain.dto;

import lombok.Data;

@Data
public class ArticleAdminPageDto {
    private Integer page = 1;
    private Integer size = 10;
    /** 状态：draft/published/archived，null=全部 */
    private String status;
    /** 标题/摘要模糊搜索，null=不限 */
    private String keyword;
}
```

```java
// ArticleAdminItemVo
package com.oyproj.api.article.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleAdminItemVo {
    private String id;
    private String title;
    private String summary;
    private String status;
    private String coverUrl;
    private LocalDateTime publishAt;
    private LocalDateTime updateAt;
    private Long views;
    private Long likes;
    private Long comments;
}
```

```java
// TagSaveDto（id 为空=新建，非空=更新）
package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagSaveDto {
    private String id;
    @NotBlank(message = "标签名不能为空")
    private String name;
    /** 1=常用(管理员预置) 0=自创 */
    private Integer isCommon;
}
```

```java
// TagAdminVo
package com.oyproj.api.article.domain.vo;

import lombok.Data;

@Data
public class TagAdminVo {
    private String id;
    private String name;
    private Integer isCommon;
}
```

```java
// SeriesSaveDto
package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SeriesSaveDto {
    private String id;
    @NotBlank(message = "系列名不能为空")
    private String name;
    private String description;
    private String code;
}
```

```java
// SeriesAdminVo
package com.oyproj.api.article.domain.vo;

import lombok.Data;

@Data
public class SeriesAdminVo {
    private String id;
    private String name;
    private String description;
    private String code;
}
```

```java
// CommentAdminPageDto
package com.oyproj.api.article.domain.dto;

import lombok.Data;

@Data
public class CommentAdminPageDto {
    private Integer page = 1;
    private Integer size = 10;
    /** 审核状态：0=待审 1=通过 2=拒绝，null=全部 */
    private Integer status;
}
```

```java
// CommentAdminItemVo
package com.oyproj.api.article.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentAdminItemVo {
    private String id;
    private String articleId;
    private String userId;
    private String content;
    private Integer status;
    private Integer isPinned;
    private LocalDateTime commentAt;
}
```

```java
// CommentAuditDto
package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentAuditDto {
    @NotBlank(message = "评论ID不能为空")
    private String commentId;
    /** 1=通过 2=拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Integer status;
    private String reason;
}
```

```java
// UserAdminPageDto
package com.oyproj.api.user.domain.dto;

import lombok.Data;

@Data
public class UserAdminPageDto {
    private Integer page = 1;
    private Integer size = 10;
    /** 用户名/邮箱模糊搜索，null=不限 */
    private String keyword;
    /** 0=禁用 1=启用，null=全部 */
    private Integer status;
}
```

```java
// UserAdminItemVo
package com.oyproj.api.user.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAdminItemVo {
    private String id;
    private String username;
    private String email;
    private Integer status;
    private String avatarUrl;
    /** 是否拥有 ADMIN 角色 */
    private Boolean admin;
    private LocalDateTime createdAt;
}
```

```java
// UserRoleAssignDto
package com.oyproj.api.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleAssignDto {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    /** true=授予 ADMIN，false=收回 */
    @NotNull(message = "admin 标记不能为空")
    private Boolean admin;
}
```

`ArticleSaveDto`（镜像，字段与 article-service 完全一致，包名不同）:

```java
package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 文章保存/发布请求参数（service-api 镜像，供 Feign 传输）
 */
@Data
public class ArticleSaveDto {
    private String id;
    @NotBlank(message = "标题不能为空")
    private String title;
    private String summary;
    @NotBlank(message = "内容不能为空")
    private String contentMd;
    private String contentHtml;
    private String coverUrl;
    private List<String> tags;
    private Integer allowComment;
}
```

- [ ] **Step 4: 写 AdminFeignConfig**

```java
package com.oyproj.api.config;

import com.oyproj.common.constant.HeaderConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 管理端 Feign 配置：把管理员的 X-User-Id/X-User-Type 透传给下游服务，
 * 下游管理接口的 @RequirePermission 依据该头放行。
 * 注意：本类不能加 @Configuration，否则会污染所有 Feign 客户端；
 * 由 Admin*Client 通过 configuration 属性显式引用。
 */
public class AdminFeignConfig {

    @Bean
    public RequestInterceptor adminIdentityPropagationInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs == null) {
                    return;
                }
                String userId = attrs.getRequest().getHeader(HeaderConstant.USER_ID.getValue());
                String userType = attrs.getRequest().getHeader(HeaderConstant.USER_TYPE.getValue());
                if (userId != null) {
                    template.header(HeaderConstant.USER_ID.getValue(), userId);
                }
                if (userType != null) {
                    template.header(HeaderConstant.USER_TYPE.getValue(), userType);
                }
            }
        };
    }
}
```

- [ ] **Step 5: 写 AdminArticleClient**

```java
package com.oyproj.api.article.client;

import com.oyproj.api.article.client.fallback.AdminArticleClientFallbackFactory;
import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.api.config.AdminFeignConfig;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章服务管理接口 Feign 客户端（admin-service 使用）
 */
@FeignClient(value = "article-service", configuration = AdminFeignConfig.class,
        fallbackFactory = AdminArticleClientFallbackFactory.class)
public interface AdminArticleClient {

    // ===== 文章管理 =====
    @PostMapping("/article/admin/page")
    Result<PageVo<List<ArticleAdminItemVo>>> adminArticlePage(@RequestBody ArticleAdminPageDto dto);

    @PostMapping("/article/draft")
    Result<String> saveDraft(@RequestBody ArticleSaveDto dto);

    @PostMapping("/article/publish")
    Result<Map<String, String>> publish(@RequestBody ArticleSaveDto dto);

    @DeleteMapping("/article/{id}")
    Result<Boolean> deleteArticle(@PathVariable("id") String id);

    // ===== 标签管理 =====
    @PostMapping("/article/admin/tag")
    Result<String> saveTag(@RequestBody TagSaveDto dto);

    @DeleteMapping("/article/admin/tag/{id}")
    Result<Boolean> deleteTag(@PathVariable("id") String id);

    @GetMapping("/article/admin/tags")
    Result<List<TagAdminVo>> listTags();

    // ===== 系列管理 =====
    @PostMapping("/article/admin/series")
    Result<String> saveSeries(@RequestBody SeriesSaveDto dto);

    @DeleteMapping("/article/admin/series/{id}")
    Result<Boolean> deleteSeries(@PathVariable("id") String id);

    @GetMapping("/article/admin/series")
    Result<List<SeriesAdminVo>> listSeries();

    // ===== 评论审核 =====
    @PostMapping("/article/comment/admin/page")
    Result<PageVo<List<CommentAdminItemVo>>> adminCommentPage(@RequestBody CommentAdminPageDto dto);

    @PostMapping("/article/comment/admin/audit")
    Result<Boolean> auditComment(@RequestBody CommentAuditDto dto);

    @DeleteMapping("/article/comment/admin/{id}")
    Result<Boolean> deleteComment(@PathVariable("id") String id);

    @PostMapping("/article/comment/admin/{id}/pin")
    Result<Boolean> pinComment(@PathVariable("id") String id, @RequestParam("pinned") Integer pinned);
}
```

`AdminArticleClientFallbackFactory`（照抄 UserClientFallbackFactory 结构，全部返回 `Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"))` 并 log.warn，方法体按上面接口逐一实现——adminArticlePage 返回 `Result.error(...)` 即可（泛型擦除））。

- [ ] **Step 6: 写 AdminUserClient**

```java
package com.oyproj.api.user.client;

import com.oyproj.api.config.AdminFeignConfig;
import com.oyproj.api.user.client.fallback.AdminUserClientFallbackFactory;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户服务管理接口 Feign 客户端（admin-service 使用）
 */
@FeignClient(value = "user-service", configuration = AdminFeignConfig.class,
        fallbackFactory = AdminUserClientFallbackFactory.class)
public interface AdminUserClient {

    @PostMapping("/admin/users/page")
    Result<PageVo<List<UserAdminItemVo>>> adminUserPage(@RequestBody UserAdminPageDto dto);

    @PostMapping("/admin/users/{id}/ban")
    Result<Boolean> banUser(@PathVariable("id") String id);

    @PostMapping("/admin/users/{id}/unban")
    Result<Boolean> unbanUser(@PathVariable("id") String id);

    @PostMapping("/admin/users/role")
    Result<Boolean> assignRole(@RequestBody UserRoleAssignDto dto);
}
```

`AdminUserClientFallbackFactory`：同上结构，4 个方法全部返回错误 Result。

- [ ] **Step 7: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add oy-blog-service/service-api/src/main/java/com/oyproj/api/
git commit -m "feat: service-api 新增管理 Feign 客户端与跨服务 DTO

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: article-service 文章管理接口（管理列表/标签/系列）

**Files:**
- Create: `oy-blog-service/article-service/src/main/java/com/oyproj/controller/ArticleAdminController.java`
- Create: `oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleAdminBizService.java`
- Create: `oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleAdminBizServiceImpl.java`
- Test: `oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/ArticleAdminBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 5 的 DTO/VO（ArticleAdminPageDto/ArticleAdminItemVo/TagSaveDto/TagAdminVo/SeriesSaveDto/SeriesAdminVo）
- Produces: `POST /article/admin/page`、`POST /article/admin/tag`、`DELETE /article/admin/tag/{id}`、`GET /article/admin/tags`、`POST /article/admin/series`、`DELETE /article/admin/series/{id}`、`GET /article/admin/series`（全部挂 @RequirePermission）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.Tag;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleStatsMapper;
import com.oyproj.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleAdminBizServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private ArticleStatsMapper articleStatsMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private com.oyproj.mapper.ArticleSeriesMapper articleSeriesMapper;

    private ArticleAdminBizServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        MessageSource mockMsg = mock(MessageSource.class);
        lenient().when(mockMsg.getMessage(anyString(), any(), any())).thenReturn("OK");
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockMsg);
        service = new ArticleAdminBizServiceImpl(articleMapper, articleStatsMapper, tagMapper, articleSeriesMapper);
    }

    private Article article(String id, String status) {
        Article a = new Article();
        a.setId(id);
        a.setTitle("标题" + id);
        a.setStatus(status);
        a.setUpdateAt(LocalDateTime.now());
        return a;
    }

    @Test
    void adminPage_returnsPageVoWithStats() {
        ArticleAdminPageDto dto = new ArticleAdminPageDto();
        dto.setPage(1);
        dto.setSize(10);
        dto.setStatus("published");

        Page<Article> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(article("a1", "published")));
        mpPage.setTotal(1);
        when(articleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mpPage);

        var result = service.adminPage(dto);

        assertTrue(result.getIsSuccess());
        assertEquals(1L, result.getData().getTotal());
        ArticleAdminItemVo item = result.getData().getData().get(0);
        assertEquals("a1", item.getId());
        assertEquals("published", item.getStatus());
    }

    @Test
    void saveTag_new_insertsWithGeneratedId() {
        TagSaveDto dto = new TagSaveDto();
        dto.setName("Java");
        when(tagMapper.insert(any(Tag.class))).thenReturn(1);

        var result = service.saveTag(dto);

        assertTrue(result.getIsSuccess());
        assertNotNull(result.getData());
        verify(tagMapper).insert(any(Tag.class));
    }

    @Test
    void deleteTag_missing_returnsFail() {
        when(tagMapper.deleteById("t-none")).thenReturn(0);

        var result = service.deleteTag("t-none");

        assertFalse(result.getIsSuccess());
    }

    @Test
    void listTags_returnsAll() {
        Tag tag = new Tag();
        tag.setId("t1");
        tag.setName("Java");
        when(tagMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(tag));

        var result = service.listTags();

        assertTrue(result.getIsSuccess());
        TagAdminVo vo = result.getData().get(0);
        assertEquals("Java", vo.getName());
    }

    @Test
    void saveSeries_new_insertsWithGeneratedId() {
        SeriesSaveDto dto = new SeriesSaveDto();
        dto.setName("Spring 系列");
        when(articleSeriesMapper.insert(any(com.oyproj.domain.entity.ArticleSeries.class))).thenReturn(1);

        var result = service.saveSeries(dto);

        assertTrue(result.getIsSuccess());
        assertNotNull(result.getData());
        verify(articleSeriesMapper).insert(any(com.oyproj.domain.entity.ArticleSeries.class));
    }

    @Test
    void listSeries_returnsAll() {
        com.oyproj.domain.entity.ArticleSeries series = new com.oyproj.domain.entity.ArticleSeries();
        series.setId("s1");
        series.setName("Spring 系列");
        when(articleSeriesMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(series));

        var result = service.listSeries();

        assertTrue(result.getIsSuccess());
        assertEquals("Spring 系列", result.getData().get(0).getName());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/article-service test -Dtest=ArticleAdminBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（ArticleAdminBizServiceImpl 不存在）

- [ ] **Step 3: 写实现**

`service/ArticleAdminBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.SeriesAdminVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 文章管理后台业务
 */
public interface ArticleAdminBizService {

    /** 管理视角文章分页列表（全状态筛选，不含软删除） */
    Result<PageVo<List<ArticleAdminItemVo>>> adminPage(ArticleAdminPageDto dto);

    /** 新建/更新标签（id 空=新建） */
    Result<String> saveTag(TagSaveDto dto);

    /** 删除标签 */
    Result<Boolean> deleteTag(String id);

    /** 标签全量列表 */
    Result<List<TagAdminVo>> listTags();

    /** 新建/更新系列 */
    Result<String> saveSeries(SeriesSaveDto dto);

    /** 删除系列 */
    Result<Boolean> deleteSeries(String id);

    /** 系列全量列表 */
    Result<List<SeriesAdminVo>> listSeries();
}
```

`service/impl/ArticleAdminBizServiceImpl.java`（关键实现逻辑如下，其余照模板）:

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.SeriesAdminVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.StringUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.entity.Tag;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleSeriesMapper;
import com.oyproj.mapper.ArticleStatsMapper;
import com.oyproj.mapper.TagMapper;
import com.oyproj.service.ArticleAdminBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章管理后台业务实现（只做管理视角查询与标签/系列维护，写文章仍走 ArticleBizService）
 */
@Service
@RequiredArgsConstructor
public class ArticleAdminBizServiceImpl extends ArticleBaseBizService implements ArticleAdminBizService {

    private final ArticleMapper articleMapper;
    private final ArticleStatsMapper articleStatsMapper;
    private final TagMapper tagMapper;
    private final ArticleSeriesMapper seriesMapper;

    @Override
    public Result<PageVo<List<ArticleAdminItemVo>>> adminPage(ArticleAdminPageDto dto) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Article::getDeletedAt)
                .eq(StringUtils.hasText(dto.getStatus()), Article::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(Article::getTitle, dto.getKeyword())
                        .or()
                        .like(Article::getSummary, dto.getKeyword()))
                .orderByDesc(Article::getUpdateAt);
        Page<Article> page = articleMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        // 批量补统计（浏览/点赞/评论）
        List<String> ids = page.getRecords().stream().map(Article::getId).toList();
        Map<String, ArticleStats> statsMap = ids.isEmpty() ? Collections.emptyMap()
                : articleStatsMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(ArticleStats::getArticleId, Function.identity()));

        List<ArticleAdminItemVo> items = page.getRecords().stream().map(a -> {
            ArticleAdminItemVo vo = copyProperties(a, ArticleAdminItemVo.class);
            ArticleStats s = statsMap.get(a.getId());
            if (s != null) {
                vo.setViews(s.getViews());
                vo.setLikes(s.getLikes());
                vo.setComments(s.getComments());
            }
            return vo;
        }).toList();

        PageVo<List<ArticleAdminItemVo>> resultPage = new PageVo<>(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), (int) page.getPages(), items);
        return Result.ok(resultPage);
    }

    @Override
    public Result<String> saveTag(TagSaveDto dto) {
        Tag tag = copyProperties(dto, Tag.class);
        if (!StringUtils.hasText(tag.getId())) {
            tag.setId(getId());
            tagMapper.insert(tag);
        } else {
            tagMapper.updateById(tag);
        }
        return Result.ok(tag.getId());
    }

    @Override
    public Result<Boolean> deleteTag(String id) {
        boolean ok = tagMapper.deleteById(id) > 0;
        return ok ? Result.ok(true) : Result.error(false);
    }

    @Override
    public Result<List<TagAdminVo>> listTags() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getCreatedAt));
        return Result.ok(copyList(tags, TagAdminVo.class));
    }

    @Override
    public Result<String> saveSeries(SeriesSaveDto dto) {
        ArticleSeries series = copyProperties(dto, ArticleSeries.class);
        if (!StringUtils.hasText(series.getId())) {
            series.setId(getId());
            seriesMapper.insert(series);
        } else {
            seriesMapper.updateById(series);
        }
        return Result.ok(series.getId());
    }

    @Override
    public Result<Boolean> deleteSeries(String id) {
        boolean ok = seriesMapper.deleteById(id) > 0;
        return ok ? Result.ok(true) : Result.error(false);
    }

    @Override
    public Result<List<SeriesAdminVo>> listSeries() {
        List<ArticleSeries> series = seriesMapper.selectList(
                new LambdaQueryWrapper<ArticleSeries>().orderByAsc(ArticleSeries::getCreatedAt));
        return Result.ok(copyList(series, SeriesAdminVo.class));
    }
}
```

`controller/ArticleAdminController.java`:

```java
package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.SeriesAdminVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.ArticleAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章管理后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "文章管理后台控制器", description = "管理视角文章列表、标签与系列维护")
@RestController
@RequestMapping("/article/admin")
@RequiredArgsConstructor
public class ArticleAdminController {

    private final ArticleAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:article:read")
    @Operation(summary = "管理视角文章分页列表")
    public Result<PageVo<List<ArticleAdminItemVo>>> adminPage(@RequestBody ArticleAdminPageDto dto) {
        return biz.adminPage(dto);
    }

    @PostMapping("/tag")
    @RequirePermission("admin:article:write")
    @Operation(summary = "新建或更新标签")
    public Result<String> saveTag(@RequestBody TagSaveDto dto) {
        return biz.saveTag(dto);
    }

    @DeleteMapping("/tag/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除标签")
    public Result<Boolean> deleteTag(@PathVariable("id") String id) {
        return biz.deleteTag(id);
    }

    @GetMapping("/tags")
    @RequirePermission("admin:article:read")
    @Operation(summary = "标签全量列表")
    public Result<List<TagAdminVo>> listTags() {
        return biz.listTags();
    }

    @PostMapping("/series")
    @RequirePermission("admin:article:write")
    @Operation(summary = "新建或更新系列")
    public Result<String> saveSeries(@RequestBody SeriesSaveDto dto) {
        return biz.saveSeries(dto);
    }

    @DeleteMapping("/series/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除系列")
    public Result<Boolean> deleteSeries(@PathVariable("id") String id) {
        return biz.deleteSeries(id);
    }

    @GetMapping("/series")
    @RequirePermission("admin:article:read")
    @Operation(summary = "系列全量列表")
    public Result<List<SeriesAdminVo>> listSeries() {
        return biz.listSeries();
    }
}
```

（注：`ArticleBaseBizService`（`com.oyproj.base.ArticleBaseBizService`）无 final 字段、无参构造（已核实），故实现类 `@RequiredArgsConstructor` 生成的构造器即上面 4 参形态；`StringUtils` 用 `org.springframework.util.StringUtils`。）

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oy-blog-service/article-service/src/main/java/com/oyproj/controller/ArticleAdminController.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/service/ArticleAdminBizService.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleAdminBizServiceImpl.java
git commit -m "feat: article-service 管理列表与标签系列维护接口

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: article-service 评论审核（status 字段 + 读路径过滤 + 审核接口）

**Files:**
- Create: `doc/sql/comment_moderation_migration.sql`
- Modify: `oy-blog-service/article-service/src/main/java/com/oyproj/domain/entity/Comment.java`（加 status、isDeleted）
- Modify: `oy-blog-service/article-service/src/main/java/com/oyproj/domain/entity/CommentReply.java`（加 status、isDeleted）
- Modify: `oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleCommentBizServiceImpl.java`（addComment/addReply 默认待审；commentCount/listComments/listReplies 过滤 status=1）
- Create: `oy-blog-service/article-service/src/main/java/com/oyproj/controller/CommentAdminController.java`
- Create: `oy-blog-service/article-service/src/main/java/com/oyproj/service/CommentAdminBizService.java`
- Create: `oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/CommentAdminBizServiceImpl.java`
- Test: `oy-blog-service/article-service/src/test/java/com/oyproj/service/impl/CommentAdminBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 5 的 CommentAdminPageDto/CommentAdminItemVo/CommentAuditDto
- Produces: `POST /article/comment/admin/page`、`POST /article/comment/admin/audit`、`DELETE /article/comment/admin/{id}`、`POST /article/comment/admin/{id}/pin`；新评论默认 status=0（待审），用户端读接口只返回 status=1

- [ ] **Step 1: 写迁移 SQL**

`doc/sql/comment_moderation_migration.sql`:

```sql
-- 评论审核迁移（先执行 SQL 再发布新代码；旧代码不受新增字段影响）
ALTER TABLE `comment`
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态 0=待审 1=通过 2=拒绝' AFTER `content`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是' AFTER `status`;

ALTER TABLE `comment_reply`
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态 0=待审 1=通过 2=拒绝' AFTER `content`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是' AFTER `status`;
```

- [ ] **Step 2: 写失败测试**

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Comment;
import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.mapper.CommentMapper;
import com.oyproj.mapper.ModerationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentAdminBizServiceImplTest {

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private ModerationLogMapper moderationLogMapper;

    private CommentAdminBizServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        MessageSource mockMsg = mock(MessageSource.class);
        lenient().when(mockMsg.getMessage(anyString(), any(), any())).thenReturn("OK");
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockMsg);
        service = new CommentAdminBizServiceImpl(commentMapper, moderationLogMapper);
    }

    @Test
    void adminPage_pending_returnsOnlyPending() {
        CommentAdminPageDto dto = new CommentAdminPageDto();
        dto.setStatus(0);
        Comment c = new Comment();
        c.setId("c1");
        c.setContent("待审评论");
        c.setStatus(0);
        c.setCommentAt(LocalDateTime.now());
        Page<Comment> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(c));
        mpPage.setTotal(1);
        when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mpPage);

        var result = service.adminPage(dto);

        assertTrue(result.getIsSuccess());
        assertEquals(0, result.getData().getData().get(0).getStatus());
    }

    @Test
    void audit_approve_updatesStatusAndWritesModerationLog() {
        Comment c = new Comment();
        c.setId("c1");
        c.setArticleId("a1");
        c.setStatus(0);
        when(commentMapper.selectById("c1")).thenReturn(c);
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        CommentAuditDto dto = new CommentAuditDto();
        dto.setCommentId("c1");
        dto.setStatus(1);
        dto.setReason("内容正常");

        var result = service.audit(dto);

        assertTrue(result.getIsSuccess());
        assertEquals(1, c.getStatus());
        verify(moderationLogMapper).insert(any(ModerationLog.class));
    }

    @Test
    void audit_notFound_returnsFail() {
        when(commentMapper.selectById("c-none")).thenReturn(null);
        CommentAuditDto dto = new CommentAuditDto();
        dto.setCommentId("c-none");
        dto.setStatus(1);

        var result = service.audit(dto);

        assertFalse(result.getIsSuccess());
        verify(moderationLogMapper, never()).insert(any());
    }

    @Test
    void pin_switchesIsPinned() {
        Comment c = new Comment();
        c.setId("c1");
        c.setIsPinned(0);
        when(commentMapper.selectById("c1")).thenReturn(c);
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        var result = service.pin("c1", 1);

        assertTrue(result.getIsSuccess());
        assertEquals(1, c.getIsPinned());
    }
}
```

（注：实现类继承 `com.oyproj.base.ArticleBaseBizService`（无 final 字段，已核实），`@RequiredArgsConstructor` 生成上面测试所用的 2 参构造器。）

- [ ] **Step 3: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/article-service test -Dtest=CommentAdminBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 4: 实体加字段**

Comment 增加（@TableLogic 作用于 isDeleted）：

```java
    /**
     * 审核状态 0=待审 1=通过 2=拒绝
     */
    @TableField("status")
    private Integer status;

    /**
     * 逻辑删除 0=否 1=是
     */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
```

CommentReply 同样增加（无 isPinned 字段即可，其余一致）。

- [ ] **Step 5: 用户端读路径过滤 + 新评论默认待审**

`ArticleCommentBizServiceImpl` 中：
- `addComment`：构造 Comment 后 `comment.setStatus(0);`（新评论默认待审）
- `addReply`：构造 CommentReply 后 `reply.setStatus(0);`
- `commentCount`/`listComments`/`listReplies` 的查询 wrapper 均追加 `.eq(Comment::getStatus, 1)`（reply 同理），保证只有审核通过的评论对用户可见

- [ ] **Step 6: 写 CommentAdminBizService 与实现**

`service/CommentAdminBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 评论审核后台业务
 */
public interface CommentAdminBizService {

    /** 评论分页列表（按审核状态筛选，默认待审） */
    Result<PageVo<List<CommentAdminItemVo>>> adminPage(CommentAdminPageDto dto);

    /** 审核：1=通过 2=拒绝，写审核日志 */
    Result<Boolean> audit(CommentAuditDto dto);

    /** 删除评论（逻辑删除） */
    Result<Boolean> delete(String id);

    /** 置顶/取消置顶（pinned: 1=置顶 0=取消） */
    Result<Boolean> pin(String id, Integer pinned);
}
```

`service/impl/CommentAdminBizServiceImpl.java` 关键实现：

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.entity.Comment;
import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.mapper.CommentMapper;
import com.oyproj.mapper.ModerationLogMapper;
import com.oyproj.service.CommentAdminBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论审核后台业务实现
 */
@Service
@RequiredArgsConstructor
public class CommentAdminBizServiceImpl extends ArticleBaseBizService implements CommentAdminBizService {

    private final CommentMapper commentMapper;
    private final ModerationLogMapper moderationLogMapper;

    @Override
    public Result<PageVo<List<CommentAdminItemVo>>> adminPage(CommentAdminPageDto dto) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, Comment::getStatus, dto.getStatus())
                .orderByAsc(Comment::getStatus)
                .orderByDesc(Comment::getCommentAt);
        Page<Comment> page = commentMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), wrapper);
        List<CommentAdminItemVo> items = copyList(page.getRecords(), CommentAdminItemVo.class);
        PageVo<List<CommentAdminItemVo>> resultPage = new PageVo<>(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), (int) page.getPages(), items);
        return Result.ok(resultPage);
    }

    @Override
    public Result<Boolean> audit(CommentAuditDto dto) {
        Comment comment = commentMapper.selectById(dto.getCommentId());
        if (comment == null) {
            return Result.error(false);
        }
        comment.setStatus(dto.getStatus());
        commentMapper.updateById(comment);

        ModerationLog log = new ModerationLog();
        log.setId(getId());
        log.setArticleId(comment.getArticleId());
        log.setAction(dto.getStatus() == 1 ? "approve" : "reject");
        log.setReason(dto.getReason());
        log.setOperatorId(getUserId());
        log.setActedAt(LocalDateTime.now());
        moderationLogMapper.insert(log);
        return Result.ok(true);
    }

    @Override
    public Result<Boolean> delete(String id) {
        boolean ok = commentMapper.deleteById(id) > 0;
        return ok ? Result.ok(true) : Result.error(false);
    }

    @Override
    public Result<Boolean> pin(String id, Integer pinned) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            return Result.error(false);
        }
        comment.setIsPinned(pinned);
        commentMapper.updateById(comment);
        return Result.ok(true);
    }
}
```

（`ArticleBaseBizService` 的 `getUserId()` 取请求头 X-User-Id，正是 Feign 透传的管理员 ID。若其构造有额外 final 依赖，按编译错误在构造器补齐注入。）

`controller/CommentAdminController.java`:

```java
package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.CommentAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论审核后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "评论审核后台控制器", description = "评论待审列表、审核、删除与置顶")
@RestController
@RequestMapping("/article/comment/admin")
@RequiredArgsConstructor
public class CommentAdminController {

    private final CommentAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:comment:read")
    @Operation(summary = "评论分页列表")
    public Result<PageVo<List<CommentAdminItemVo>>> adminPage(@RequestBody CommentAdminPageDto dto) {
        return biz.adminPage(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "审核评论（通过/拒绝）")
    public Result<Boolean> audit(@RequestBody CommentAuditDto dto) {
        return biz.audit(dto);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "删除评论")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return biz.delete(id);
    }

    @PostMapping("/{id}/pin")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "置顶/取消置顶评论")
    public Result<Boolean> pin(@PathVariable("id") String id, @RequestParam("pinned") Integer pinned) {
        return biz.pin(id, pinned);
    }
}
```

- [ ] **Step 7: 跑测试确认通过**（含回归：现有 ArticleCommentBizServiceImplTest 必须仍绿）

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/article-service test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 全部 PASS

- [ ] **Step 8: Commit**

```bash
git add doc/sql/comment_moderation_migration.sql \
        oy-blog-service/article-service/src/main/java/com/oyproj/domain/entity/Comment.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/domain/entity/CommentReply.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/ArticleCommentBizServiceImpl.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/controller/CommentAdminController.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/service/CommentAdminBizService.java \
        oy-blog-service/article-service/src/main/java/com/oyproj/service/impl/CommentAdminBizServiceImpl.java
git commit -m "feat: 评论审核状态字段、读路径过滤与审核接口

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: user-service 用户管理接口

**Files:**
- Create: `oy-blog-service/user-service/src/main/java/com/oyproj/controller/UserAdminController.java`
- Create: `oy-blog-service/user-service/src/main/java/com/oyproj/service/UserAdminBizService.java`
- Create: `oy-blog-service/user-service/src/main/java/com/oyproj/service/impl/UserAdminBizServiceImpl.java`
- Test: `oy-blog-service/user-service/src/test/java/com/oyproj/service/impl/UserAdminBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 5 的 UserAdminPageDto/UserAdminItemVo/UserRoleAssignDto；UserMapper/UserRoleMapper/RoleMapper
- Produces: `POST /admin/users/page`、`POST /admin/users/{id}/ban`、`POST /admin/users/{id}/unban`、`POST /admin/users/role`；封禁/角色变更会清除该用户 Redis 会话（强制下线/重登）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Role;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.entity.UserRole;
import com.oyproj.mapper.RoleMapper;
import com.oyproj.mapper.UserMapper;
import com.oyproj.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAdminBizServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private com.oyproj.dao.UserDao userDao;
    @Mock
    private CommonCache commonCache;

    private UserAdminBizServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        MessageSource mockMsg = mock(MessageSource.class);
        lenient().when(mockMsg.getMessage(anyString(), any(), any())).thenReturn("OK");
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockMsg);
        service = new UserAdminBizServiceImpl(userMapper, userRoleMapper, roleMapper, userDao, commonCache);
    }

    @Test
    void adminPage_keywordFilter_returnsPage() {
        UserAdminPageDto dto = new UserAdminPageDto();
        dto.setKeyword("oy");
        User u = new User();
        u.setId("u1");
        u.setUsername("oywq3000");
        u.setStatus(1);
        Page<User> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(u));
        mpPage.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mpPage);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = service.adminPage(dto);

        assertTrue(result.getIsSuccess());
        UserAdminItemVo vo = result.getData().getData().get(0);
        assertEquals("oywq3000", vo.getUsername());
        assertFalse(vo.getAdmin());
    }

    @Test
    void banUser_setsStatus0AndClearsSession() {
        User u = new User();
        u.setId("u1");
        u.setStatus(1);
        when(userMapper.selectById("u1")).thenReturn(u);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        var result = service.ban("u1");

        assertTrue(result.getIsSuccess());
        assertEquals(0, u.getStatus());
        verify(commonCache).remove(CachePrefix.USER_ID.getPrefix() + "u1");
        verify(commonCache).remove(CachePrefix.REFRESH_TOKEN.getPrefix() + "u1");
    }

    @Test
    void assignRole_grant_insertsUserRole() {
        Role adminRole = new Role();
        adminRole.setId("role-admin");
        adminRole.setCode("ADMIN");
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(adminRole);
        when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        UserRoleAssignDto dto = new UserRoleAssignDto();
        dto.setUserId("u1");
        dto.setAdmin(true);

        var result = service.assignRole(dto);

        assertTrue(result.getIsSuccess());
        verify(userRoleMapper).insert(any(UserRole.class));
        verify(commonCache).remove(CachePrefix.USER_ID.getPrefix() + "u1");
    }

    @Test
    void assignRole_revoke_deletesUserRole() {
        Role adminRole = new Role();
        adminRole.setId("role-admin");
        adminRole.setCode("ADMIN");
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(adminRole);

        UserRoleAssignDto dto = new UserRoleAssignDto();
        dto.setUserId("u1");
        dto.setAdmin(false);

        var result = service.assignRole(dto);

        assertTrue(result.getIsSuccess());
        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/user-service test -Dtest=UserAdminBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 3: 写实现**

`service/UserAdminBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 用户管理后台业务
 */
public interface UserAdminBizService {

    /** 用户分页列表（关键字/状态筛选，标注是否管理员） */
    Result<PageVo<List<UserAdminItemVo>>> adminPage(UserAdminPageDto dto);

    /** 封禁用户（status=0 并踢下线） */
    Result<Boolean> ban(String id);

    /** 解封用户（status=1） */
    Result<Boolean> unban(String id);

    /** 授予/收回 ADMIN 角色并清除会话缓存 */
    Result<Boolean> assignRole(UserRoleAssignDto dto);
}
```

`service/impl/UserAdminBizServiceImpl.java` 关键实现：

```java
package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.StringUtils;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.entity.Role;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.entity.UserRole;
import com.oyproj.mapper.RoleMapper;
import com.oyproj.mapper.UserMapper;
import com.oyproj.mapper.UserRoleMapper;
import com.oyproj.service.UserAdminBizService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理后台业务实现
 * 注意：UserBizBase 带 final 字段（userDao/cache），lombok 不生成父类字段的构造参数，
 * 必须像 UserAuthBizServiceImpl 一样手动写构造器。
 */
@Service
public class UserAdminBizServiceImpl extends UserBizBase implements UserAdminBizService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserAdminBizServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper,
                                   RoleMapper roleMapper, UserDao userDao, CommonCache cache) {
        super(userDao, cache);
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public Result<PageVo<List<UserAdminItemVo>>> adminPage(UserAdminPageDto dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, User::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(User::getUsername, dto.getKeyword())
                        .or()
                        .like(User::getEmail, dto.getKeyword()))
                .orderByDesc(User::getCreatedAt);
        Page<User> page = userMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        // 批量查 ADMIN 关联
        List<String> ids = page.getRecords().stream().map(User::getId).toList();
        Set<String> adminIds = ids.isEmpty() ? Set.of()
                : userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                                .in(UserRole::getUserId, ids))
                        .stream().map(UserRole::getUserId).collect(Collectors.toSet());

        List<UserAdminItemVo> items = page.getRecords().stream().map(u -> {
            UserAdminItemVo vo = copyProperties(u, UserAdminItemVo.class);
            vo.setAdmin(adminIds.contains(u.getId()));
            return vo;
        }).toList();
        PageVo<List<UserAdminItemVo>> resultPage = new PageVo<>(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), (int) page.getPages(), items);
        return Result.ok(resultPage);
    }

    @Override
    public Result<Boolean> ban(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(false);
        }
        user.setStatus(0);
        userMapper.updateById(user);
        clearSession(id);
        return Result.ok(true);
    }

    @Override
    public Result<Boolean> unban(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(false);
        }
        user.setStatus(1);
        userMapper.updateById(user);
        return Result.ok(true);
    }

    @Override
    public Result<Boolean> assignRole(UserRoleAssignDto dto) {
        Role adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (adminRole == null) {
            return Result.error(false);
        }
        if (Boolean.TRUE.equals(dto.getAdmin())) {
            long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, dto.getUserId())
                    .eq(UserRole::getRoleId, adminRole.getId()));
            if (exists == 0) {
                UserRole userRole = new UserRole();
                userRole.setId(getId());
                userRole.setUserId(dto.getUserId());
                userRole.setRoleId(adminRole.getId());
                userRoleMapper.insert(userRole);
            }
        } else {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, dto.getUserId())
                    .eq(UserRole::getRoleId, adminRole.getId()));
        }
        clearSession(dto.getUserId());
        return Result.ok(true);
    }

    /** 清除用户会话，强制重新登录以刷新角色 */
    private void clearSession(String userId) {
        cache.remove(CachePrefix.USER_ID.getPrefix() + userId);
        cache.remove(CachePrefix.REFRESH_TOKEN.getPrefix() + userId);
    }
}
```

`controller/UserAdminController.java`:

```java
package com.oyproj.controller;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.UserAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "用户管理后台控制器", description = "用户列表、封禁与角色分配")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:user:read")
    @Operation(summary = "用户分页列表")
    public Result<PageVo<List<UserAdminItemVo>>> adminPage(@RequestBody UserAdminPageDto dto) {
        return biz.adminPage(dto);
    }

    @PostMapping("/{id}/ban")
    @RequirePermission("admin:user:write")
    @Operation(summary = "封禁用户")
    public Result<Boolean> ban(@PathVariable("id") String id) {
        return biz.ban(id);
    }

    @PostMapping("/{id}/unban")
    @RequirePermission("admin:user:write")
    @Operation(summary = "解封用户")
    public Result<Boolean> unban(@PathVariable("id") String id) {
        return biz.unban(id);
    }

    @PostMapping("/role")
    @RequirePermission("admin:user:write")
    @Operation(summary = "授予/收回 ADMIN 角色")
    public Result<Boolean> assignRole(@RequestBody UserRoleAssignDto dto) {
        return biz.assignRole(dto);
    }
}
```

（注：UserBizBase 的 `@RequiredArgsConstructor` 构造为 `(UserDao, CommonCache)`，实现类手动构造器 `super(userDao, cache)` 与 UserAuthBizServiceImpl 的写法一致（已核实）；`clearSession` 用的 `cache` 是 UserBizBase 的 protected 字段。）

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oy-blog-service/user-service/src/main/java/com/oyproj/controller/UserAdminController.java \
        oy-blog-service/user-service/src/main/java/com/oyproj/service/UserAdminBizService.java \
        oy-blog-service/user-service/src/main/java/com/oyproj/service/impl/UserAdminBizServiceImpl.java
git commit -m "feat: user-service 用户管理接口（列表/封禁/角色分配）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: admin-service 文章管理模块（BFF）

**Files:**
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminArticleController.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminArticleBizService.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminArticleBizServiceImpl.java`
- Test: `oy-blog-service/admin-service/src/test/java/com/oyproj/service/impl/AdminArticleBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 5 的 AdminArticleClient 与 DTO/VO
- Produces: `/admin/article/page`、`/admin/article/draft`、`/admin/article/publish`、`DELETE /admin/article/{id}`、`/admin/article/tag`（POST/DELETE）、`/admin/article/tags`、`/admin/article/series`（POST/DELETE）、`/admin/article/series`（GET）——纯转发 Feign，Feign 返回什么就透传什么

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.common.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminArticleBizServiceImplTest {

    @Mock
    private AdminArticleClient client;

    @InjectMocks
    private AdminArticleBizServiceImpl service;

    @Test
    void page_passesThroughFeignResult() {
        ArticleAdminPageDto dto = new ArticleAdminPageDto();
        Result<com.oyproj.common.domain.vo.PageVo<java.util.List<com.oyproj.api.article.domain.vo.ArticleAdminItemVo>>> expected =
                Result.ok(new com.oyproj.common.domain.vo.PageVo<>(1, 10, 0L, 0, java.util.List.of()));
        when(client.adminArticlePage(dto)).thenReturn(expected);

        var result = service.page(dto);

        assertSame(expected, result);
        verify(client).adminArticlePage(dto);
    }

    @Test
    void saveTag_passesThrough() {
        TagSaveDto dto = new TagSaveDto();
        dto.setName("Java");
        when(client.saveTag(dto)).thenReturn(Result.ok("t1"));

        var result = service.saveTag(dto);

        assertEquals("t1", result.getData());
    }

    @Test
    void downstreamFailure_propagatesErrorResult() {
        when(client.listTags()).thenReturn(Result.error(false));

        var result = service.listTags();

        assertFalse(result.getIsSuccess());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/admin-service test -Dtest=AdminArticleBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 3: 写实现**（薄编排层，无业务逻辑）

`service/AdminArticleBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;
import java.util.Map;

/**
 * 文章管理 BFF 业务：透传 Feign 调用 article-service
 */
public interface AdminArticleBizService {

    Result<PageVo<List<ArticleAdminItemVo>>> page(ArticleAdminPageDto dto);

    Result<String> draft(ArticleSaveDto dto);

    Result<Map<String, String>> publish(ArticleSaveDto dto);

    Result<Boolean> delete(String id);

    Result<String> saveTag(TagSaveDto dto);

    Result<Boolean> deleteTag(String id);

    Result<List<TagAdminVo>> listTags();

    Result<String> saveSeries(SeriesSaveDto dto);

    Result<Boolean> deleteSeries(String id);

    Result<List<SeriesAdminVo>> listSeries();
}
```

`service/impl/AdminArticleBizServiceImpl.java`:

```java
package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminArticleBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文章管理 BFF 实现：全部直接透传 Feign 结果
 */
@Service
@RequiredArgsConstructor
public class AdminArticleBizServiceImpl extends AdminBizBase implements AdminArticleBizService {

    private final AdminArticleClient client;

    @Override
    public Result<PageVo<List<ArticleAdminItemVo>>> page(ArticleAdminPageDto dto) {
        return client.adminArticlePage(dto);
    }

    @Override
    public Result<String> draft(ArticleSaveDto dto) {
        return client.saveDraft(dto);
    }

    @Override
    public Result<Map<String, String>> publish(ArticleSaveDto dto) {
        return client.publish(dto);
    }

    @Override
    public Result<Boolean> delete(String id) {
        return client.deleteArticle(id);
    }

    @Override
    public Result<String> saveTag(TagSaveDto dto) {
        return client.saveTag(dto);
    }

    @Override
    public Result<Boolean> deleteTag(String id) {
        return client.deleteTag(id);
    }

    @Override
    public Result<List<TagAdminVo>> listTags() {
        return client.listTags();
    }

    @Override
    public Result<String> saveSeries(SeriesSaveDto dto) {
        return client.saveSeries(dto);
    }

    @Override
    public Result<Boolean> deleteSeries(String id) {
        return client.deleteSeries(id);
    }

    @Override
    public Result<List<SeriesAdminVo>> listSeries() {
        return client.listSeries();
    }
}
```

`controller/AdminArticleController.java`:

```java
package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminArticleBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端文章管理控制器（BFF 入口，管理前端只调这里）
 */
@Tag(name = "管理端文章控制器", description = "管理端文章列表、发布、标签与系列")
@RestController
@RequestMapping("/admin/article")
@RequiredArgsConstructor
public class AdminArticleController {

    private final AdminArticleBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:article:read")
    @Operation(summary = "管理视角文章分页列表")
    public Result<PageVo<List<ArticleAdminItemVo>>> page(@RequestBody ArticleAdminPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/draft")
    @RequirePermission("admin:article:write")
    @Operation(summary = "保存草稿")
    public Result<String> draft(@RequestBody ArticleSaveDto dto) {
        return biz.draft(dto);
    }

    @PostMapping("/publish")
    @RequirePermission("admin:article:write")
    @Operation(summary = "发布文章")
    public Result<Map<String, String>> publish(@RequestBody ArticleSaveDto dto) {
        return biz.publish(dto);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除文章")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return biz.delete(id);
    }

    @PostMapping("/tag")
    @RequirePermission("admin:article:write")
    @Operation(summary = "新建或更新标签")
    public Result<String> saveTag(@RequestBody TagSaveDto dto) {
        return biz.saveTag(dto);
    }

    @DeleteMapping("/tag/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除标签")
    public Result<Boolean> deleteTag(@PathVariable("id") String id) {
        return biz.deleteTag(id);
    }

    @GetMapping("/tags")
    @RequirePermission("admin:article:read")
    @Operation(summary = "标签全量列表")
    public Result<List<TagAdminVo>> listTags() {
        return biz.listTags();
    }

    @PostMapping("/series")
    @RequirePermission("admin:article:write")
    @Operation(summary = "新建或更新系列")
    public Result<String> saveSeries(@RequestBody SeriesSaveDto dto) {
        return biz.saveSeries(dto);
    }

    @DeleteMapping("/series/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除系列")
    public Result<Boolean> deleteSeries(@PathVariable("id") String id) {
        return biz.deleteSeries(id);
    }

    @GetMapping("/series")
    @RequirePermission("admin:article:read")
    @Operation(summary = "系列全量列表")
    public Result<List<SeriesAdminVo>> listSeries() {
        return biz.listSeries();
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminArticleController.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminArticleBizService.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminArticleBizServiceImpl.java
git commit -m "feat: admin-service 文章管理 BFF 模块

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: admin-service 评论审核模块（BFF）

**Files:**
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminCommentController.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminCommentBizService.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminCommentBizServiceImpl.java`
- Test: `oy-blog-service/admin-service/src/test/java/com/oyproj/service/impl/AdminCommentBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 5 的 AdminArticleClient 评论审核方法与 CommentAdminPageDto/CommentAuditDto/CommentAdminItemVo
- Produces: `/admin/comment/page`、`/admin/comment/audit`、`DELETE /admin/comment/{id}`、`POST /admin/comment/{id}/pin`

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.common.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCommentBizServiceImplTest {

    @Mock
    private AdminArticleClient client;

    @InjectMocks
    private AdminCommentBizServiceImpl service;

    @Test
    void page_passesThrough() {
        CommentAdminPageDto dto = new CommentAdminPageDto();
        dto.setStatus(0);
        Result<com.oyproj.common.domain.vo.PageVo<java.util.List<com.oyproj.api.article.domain.vo.CommentAdminItemVo>>> expected =
                Result.ok(new com.oyproj.common.domain.vo.PageVo<>(1, 10, 5L, 1, java.util.List.of()));
        when(client.adminCommentPage(dto)).thenReturn(expected);

        assertSame(expected, service.page(dto));
    }

    @Test
    void audit_passesThrough() {
        CommentAuditDto dto = new CommentAuditDto();
        dto.setCommentId("c1");
        dto.setStatus(1);
        when(client.auditComment(dto)).thenReturn(Result.ok(true));

        assertTrue(service.audit(dto).getData());
    }

    @Test
    void downstreamError_propagates() {
        when(client.deleteComment("c1")).thenReturn(Result.error(false));

        assertFalse(service.delete("c1").getIsSuccess());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/admin-service test -Dtest=AdminCommentBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 3: 写实现**（结构与 Task 9 完全同构：接口 → impl 透传 → controller 挂 @RequirePermission）

`service/AdminCommentBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 评论审核 BFF 业务：透传 Feign 调用 article-service
 */
public interface AdminCommentBizService {

    Result<PageVo<List<CommentAdminItemVo>>> page(CommentAdminPageDto dto);

    Result<Boolean> audit(CommentAuditDto dto);

    Result<Boolean> delete(String id);

    Result<Boolean> pin(String id, Integer pinned);
}
```

`service/impl/AdminCommentBizServiceImpl.java`:

```java
package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminCommentBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCommentBizServiceImpl extends AdminBizBase implements AdminCommentBizService {

    private final AdminArticleClient client;

    @Override
    public Result<PageVo<List<CommentAdminItemVo>>> page(CommentAdminPageDto dto) {
        return client.adminCommentPage(dto);
    }

    @Override
    public Result<Boolean> audit(CommentAuditDto dto) {
        return client.auditComment(dto);
    }

    @Override
    public Result<Boolean> delete(String id) {
        return client.deleteComment(id);
    }

    @Override
    public Result<Boolean> pin(String id, Integer pinned) {
        return client.pinComment(id, pinned);
    }
}
```

`controller/AdminCommentController.java`:

```java
package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminCommentBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端评论审核控制器（BFF 入口）
 */
@Tag(name = "管理端评论审核控制器", description = "评论待审列表、审核、删除与置顶")
@RestController
@RequestMapping("/admin/comment")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminCommentBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:comment:read")
    @Operation(summary = "评论分页列表")
    public Result<PageVo<List<CommentAdminItemVo>>> page(@RequestBody CommentAdminPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "审核评论（通过/拒绝）")
    public Result<Boolean> audit(@RequestBody CommentAuditDto dto) {
        return biz.audit(dto);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "删除评论")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return biz.delete(id);
    }

    @PostMapping("/{id}/pin")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "置顶/取消置顶评论")
    public Result<Boolean> pin(@PathVariable("id") String id, @RequestParam("pinned") Integer pinned) {
        return biz.pin(id, pinned);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminCommentController.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminCommentBizService.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminCommentBizServiceImpl.java
git commit -m "feat: admin-service 评论审核 BFF 模块

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: admin-service 用户管理模块（BFF）

**Files:**
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminUserController.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminUserBizService.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminUserBizServiceImpl.java`
- Test: `oy-blog-service/admin-service/src/test/java/com/oyproj/service/impl/AdminUserBizServiceImplTest.java`

**Interfaces:**
- Consumes: Task 5 的 AdminUserClient 与 UserAdminPageDto/UserRoleAssignDto/UserAdminItemVo
- Produces: `/admin/user/page`、`POST /admin/user/{id}/ban`、`POST /admin/user/{id}/unban`、`POST /admin/user/role`

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.api.user.client.AdminUserClient;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.common.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserBizServiceImplTest {

    @Mock
    private AdminUserClient client;

    @InjectMocks
    private AdminUserBizServiceImpl service;

    @Test
    void page_passesThrough() {
        UserAdminPageDto dto = new UserAdminPageDto();
        Result<com.oyproj.common.domain.vo.PageVo<java.util.List<com.oyproj.api.user.domain.vo.UserAdminItemVo>>> expected =
                Result.ok(new com.oyproj.common.domain.vo.PageVo<>(1, 10, 3L, 1, java.util.List.of()));
        when(client.adminUserPage(dto)).thenReturn(expected);

        assertSame(expected, service.page(dto));
    }

    @Test
    void ban_passesThrough() {
        when(client.banUser("u1")).thenReturn(Result.ok(true));

        assertTrue(service.ban("u1").getData());
    }

    @Test
    void assignRole_passesThrough() {
        UserRoleAssignDto dto = new UserRoleAssignDto();
        dto.setUserId("u1");
        dto.setAdmin(true);
        when(client.assignRole(dto)).thenReturn(Result.ok(true));

        assertTrue(service.assignRole(dto).getData());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/admin-service test -Dtest=AdminUserBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 3: 写实现**（与 Task 10 同构）

`service/AdminUserBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 用户管理 BFF 业务：透传 Feign 调用 user-service
 */
public interface AdminUserBizService {

    Result<PageVo<List<UserAdminItemVo>>> page(UserAdminPageDto dto);

    Result<Boolean> ban(String id);

    Result<Boolean> unban(String id);

    Result<Boolean> assignRole(UserRoleAssignDto dto);
}
```

`service/impl/AdminUserBizServiceImpl.java`:

```java
package com.oyproj.service.impl;

import com.oyproj.api.user.client.AdminUserClient;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminUserBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserBizServiceImpl extends AdminBizBase implements AdminUserBizService {

    private final AdminUserClient client;

    @Override
    public Result<PageVo<List<UserAdminItemVo>>> page(UserAdminPageDto dto) {
        return client.adminUserPage(dto);
    }

    @Override
    public Result<Boolean> ban(String id) {
        return client.banUser(id);
    }

    @Override
    public Result<Boolean> unban(String id) {
        return client.unbanUser(id);
    }

    @Override
    public Result<Boolean> assignRole(UserRoleAssignDto dto) {
        return client.assignRole(dto);
    }
}
```

`controller/AdminUserController.java`:

```java
package com.oyproj.controller;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminUserBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端用户管理控制器（BFF 入口）
 */
@Tag(name = "管理端用户控制器", description = "用户列表、封禁与角色分配")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:user:read")
    @Operation(summary = "用户分页列表")
    public Result<PageVo<List<UserAdminItemVo>>> page(@RequestBody UserAdminPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/{id}/ban")
    @RequirePermission("admin:user:write")
    @Operation(summary = "封禁用户")
    public Result<Boolean> ban(@PathVariable("id") String id) {
        return biz.ban(id);
    }

    @PostMapping("/{id}/unban")
    @RequirePermission("admin:user:write")
    @Operation(summary = "解封用户")
    public Result<Boolean> unban(@PathVariable("id") String id) {
        return biz.unban(id);
    }

    @PostMapping("/role")
    @RequirePermission("admin:user:write")
    @Operation(summary = "授予/收回 ADMIN 角色")
    public Result<Boolean> assignRole(@RequestBody UserRoleAssignDto dto) {
        return biz.assignRole(dto);
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminUserController.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminUserBizService.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminUserBizServiceImpl.java
git commit -m "feat: admin-service 用户管理 BFF 模块

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: admin-service 统计看板（只读直连同库聚合）

**Files:**
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/dao/DashboardDao.java`
- Create: `oy-blog-service/admin-service/src/main/resources/mapper/DashboardDao.xml`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/domain/vo/DailyTrendVo.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/domain/vo/TopArticleVo.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/domain/vo/DashboardOverviewVo.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminDashboardController.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminDashboardBizService.java`
- Create: `oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminDashboardBizServiceImpl.java`
- Test: `oy-blog-service/admin-service/src/test/java/com/oyproj/service/impl/AdminDashboardBizServiceImplTest.java`

**Interfaces:**
- Produces: `GET /admin/dashboard/overview` → `Result<DashboardOverviewVo>`；`GET /admin/dashboard/trend` → `Result<List<DailyTrendVo>>`（近 30 天访问趋势）；`GET /admin/dashboard/top-articles` → `Result<List<TopArticleVo>>`（TOP10 热门文章）

- [ ] **Step 1: 写失败测试**

```java
package com.oyproj.service.impl;

import com.oyproj.common.utils.I18nUtils;
import com.oyproj.dao.DashboardDao;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardBizServiceImplTest {

    @Mock
    private DashboardDao dashboardDao;

    private AdminDashboardBizServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        MessageSource mockMsg = mock(MessageSource.class);
        lenient().when(mockMsg.getMessage(anyString(), any(), any())).thenReturn("OK");
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockMsg);
        service = new AdminDashboardBizServiceImpl(dashboardDao);
    }

    @Test
    void overview_aggregatesCounts() {
        when(dashboardDao.countPublishedArticles()).thenReturn(10L);
        when(dashboardDao.sumViews()).thenReturn(1000L);
        when(dashboardDao.sumLikes()).thenReturn(100L);
        when(dashboardDao.sumComments()).thenReturn(50L);
        when(dashboardDao.countUsers()).thenReturn(7L);

        var result = service.overview();

        assertTrue(result.getIsSuccess());
        DashboardOverviewVo vo = result.getData();
        assertEquals(10L, vo.getArticleCount());
        assertEquals(1000L, vo.getViewCount());
        assertEquals(100L, vo.getLikeCount());
        assertEquals(50L, vo.getCommentCount());
        assertEquals(7L, vo.getUserCount());
    }

    @Test
    void trend_returnsDaoData() {
        DailyTrendVo day = new DailyTrendVo("2026-08-20", 12L);
        when(dashboardDao.listDailyViews()).thenReturn(List.of(day));

        var result = service.trend();

        assertTrue(result.getIsSuccess());
        assertEquals("2026-08-20", result.getData().get(0).getDate());
        assertEquals(12L, result.getData().get(0).getCount());
    }

    @Test
    void topArticles_returnsDaoData() {
        TopArticleVo top = new TopArticleVo("a1", "热门文章", 999L);
        when(dashboardDao.listTopArticles()).thenReturn(List.of(top));

        var result = service.topArticles();

        assertTrue(result.getIsSuccess());
        assertEquals("热门文章", result.getData().get(0).getTitle());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /g/JavaWorkSpace/oy-blog && export JAVA_HOME=/d/DevelopKit/jdk-21.0.8 && mvn -pl oy-blog-service/admin-service test -Dtest=AdminDashboardBizServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 3: 写 VO**

```java
// DailyTrendVo
package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyTrendVo {
    private String date;
    private Long count;
}
```

```java
// TopArticleVo
package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopArticleVo {
    private String id;
    private String title;
    private Long views;
    private Long likes;
    private Long comments;
}
```

```java
// DashboardOverviewVo
package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVo {
    private Long articleCount;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long userCount;
}
```

- [ ] **Step 4: 写 DAO 与 XML**

`dao/DashboardDao.java`:

```java
package com.oyproj.dao;

import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.TopArticleVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 统计看板只读 DAO（直连同库统计表聚合，写操作仍走各服务）
 */
@Mapper
public interface DashboardDao {

    Long countPublishedArticles();

    Long sumViews();

    Long sumLikes();

    Long sumComments();

    Long countUsers();

    /** 近 30 天每日访问量 */
    List<DailyTrendVo> listDailyViews();

    /** 热门文章 TOP10 */
    List<TopArticleVo> listTopArticles();
}
```

`resources/mapper/DashboardDao.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.oyproj.dao.DashboardDao">

    <select id="countPublishedArticles" resultType="java.lang.Long">
        SELECT COUNT(*) FROM article WHERE status = 'published' AND deleted_at IS NULL
    </select>

    <select id="sumViews" resultType="java.lang.Long">
        SELECT IFNULL(SUM(views), 0) FROM article_stats
    </select>

    <select id="sumLikes" resultType="java.lang.Long">
        SELECT IFNULL(SUM(likes), 0) FROM article_stats
    </select>

    <select id="sumComments" resultType="java.lang.Long">
        SELECT IFNULL(SUM(comments), 0) FROM article_stats
    </select>

    <select id="countUsers" resultType="java.lang.Long">
        SELECT COUNT(*) FROM `user` WHERE id_deleted = 0
    </select>

    <select id="listDailyViews" resultType="com.oyproj.domain.vo.DailyTrendVo">
        SELECT DATE_FORMAT(view_at, '%Y-%m-%d') AS date, COUNT(*) AS count
        FROM article_log
        WHERE view_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        GROUP BY DATE_FORMAT(view_at, '%Y-%m-%d')
        ORDER BY date
    </select>

    <select id="listTopArticles" resultType="com.oyproj.domain.vo.TopArticleVo">
        SELECT a.id, a.title, s.views, s.likes, s.comments
        FROM article a
        JOIN article_stats s ON s.article_id = a.id
        WHERE a.status = 'published' AND a.deleted_at IS NULL
        ORDER BY s.views DESC
        LIMIT 10
    </select>
</mapper>
```

- [ ] **Step 5: 写 service 与 controller**

`service/AdminDashboardBizService.java`:

```java
package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;

import java.util.List;

/**
 * 统计看板业务（只读聚合）
 */
public interface AdminDashboardBizService {

    Result<DashboardOverviewVo> overview();

    Result<List<DailyTrendVo>> trend();

    Result<List<TopArticleVo>> topArticles();
}
```

`service/impl/AdminDashboardBizServiceImpl.java`:

```java
package com.oyproj.service.impl;

import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.dao.DashboardDao;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;
import com.oyproj.service.AdminDashboardBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardBizServiceImpl extends AdminBizBase implements AdminDashboardBizService {

    private final DashboardDao dashboardDao;

    @Override
    public Result<DashboardOverviewVo> overview() {
        DashboardOverviewVo vo = new DashboardOverviewVo(
                dashboardDao.countPublishedArticles(),
                dashboardDao.sumViews(),
                dashboardDao.sumLikes(),
                dashboardDao.sumComments(),
                dashboardDao.countUsers());
        return Result.ok(vo);
    }

    @Override
    public Result<List<DailyTrendVo>> trend() {
        return Result.ok(dashboardDao.listDailyViews());
    }

    @Override
    public Result<List<TopArticleVo>> topArticles() {
        return Result.ok(dashboardDao.listTopArticles());
    }
}
```

`controller/AdminDashboardController.java`:

```java
package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;
import com.oyproj.service.AdminDashboardBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端统计看板控制器
 */
@Tag(name = "管理端统计看板控制器", description = "总览卡片、访问趋势与热门文章")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardBizService biz;

    @GetMapping("/overview")
    @RequirePermission("admin:dashboard:read")
    @Operation(summary = "总览统计")
    public Result<DashboardOverviewVo> overview() {
        return biz.overview();
    }

    @GetMapping("/trend")
    @RequirePermission("admin:dashboard:read")
    @Operation(summary = "近30天访问趋势")
    public Result<List<DailyTrendVo>> trend() {
        return biz.trend();
    }

    @GetMapping("/top-articles")
    @RequirePermission("admin:dashboard:read")
    @Operation(summary = "热门文章TOP10")
    public Result<List<TopArticleVo>> topArticles() {
        return biz.topArticles();
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add oy-blog-service/admin-service/src/main/java/com/oyproj/dao/DashboardDao.java \
        oy-blog-service/admin-service/src/main/resources/mapper/DashboardDao.xml \
        oy-blog-service/admin-service/src/main/java/com/oyproj/domain/vo/ \
        oy-blog-service/admin-service/src/main/java/com/oyproj/controller/AdminDashboardController.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/AdminDashboardBizService.java \
        oy-blog-service/admin-service/src/main/java/com/oyproj/service/impl/AdminDashboardBizServiceImpl.java
git commit -m "feat: admin-service 统计看板（只读聚合）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: 全链路验收

**Files:** 无（如需记录结果：`doc/admin-service-phase1-acceptance.md`）

**验收前置**：本地/服务器 Nacos、MySQL、Redis 可用；`doc/sql/admin_seed.sql` 与 `doc/sql/comment_moderation_migration.sql` 已按顺序在 oyblog 库执行；user-service、article-service、gateway、admin-service 全部重启。

- [ ] **Step 1: 登录链路**——用博主账号登录 → 拿 token → `GET /admin-service/admin/current-user` 返回 `blogRole=ADMIN`
- [ ] **Step 2: 权限拦截**——注册一个普通 READER 账号登录 → 调 `/admin-service/admin/current-user` → 网关返回 403 Result 格式；READER 直接调 `/user-service/admin/users/page`（带自己 token）→ 同样 403
- [ ] **Step 3: 文章管理**——`POST /admin-service/admin/article/publish` 发布一篇文章 → `GET /article-service/article/read/published` 能看到；`POST /admin-service/admin/article/page` 分页列表含该文章；标签/系列增删改查正常
- [ ] **Step 4: 评论审核**——用 READER 在文章下 `POST /article-service/article/comment/add` 发评论 → 用户端 `GET .../comments` **看不到**（待审）→ 管理端 `POST /admin-service/admin/comment/page`（status=0）能看到 → `POST /admin-service/admin/comment/audit` 通过 → 用户端能看到；moderation_log 有记录
- [ ] **Step 5: 用户管理**——`POST /admin-service/admin/user/page` 列表正常 → 封禁某用户 → 该用户下一次请求被网关拒绝（会话已清）
- [ ] **Step 6: 统计看板**——`GET /admin-service/admin/dashboard/overview`、`/trend`、`/top-articles` 返回真实数据（与库里数据目测一致）
- [ ] **Step 7: 提交验收结果**——全部通过后在 `doc/admin-service-phase1-acceptance.md` 记录结果与日期；有失败项按 systematic-debugging 流程修复后复验

- [ ] **Step 8: Commit（若有记录文件）**

```bash
git add doc/admin-service-phase1-acceptance.md
git commit -m "docs: admin-service 一期验收记录

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 计划自审记录

- **Spec 覆盖**：spec 一期范围（骨架/网关/登录/文章管理/评论审核/用户管理/统计看板）逐项对应 Task 1-13；二期（站点设置/媒体库/操作日志/公告/通知）不在本计划，另立计划
- **实现决策偏离说明**（与 spec 意图一致，实现方式细化）：
  - spec ② 服务层 Security 要求 ADMIN → 实现为 admin-service 自有 SecurityConfig + 排除 common 的 SecurityConfig 扫描（单链清晰）
  - @RequirePermission 拦截器放在 common 并由下游服务共用，读 `X-User-Type` 头而非 SecurityContext（Feign 服务间调用 X-Service-Call 会短路 AuthFilter，头更可靠）
  - BFF Feign 调用透传管理员身份头（AdminFeignConfig），下游管理接口因此无需信任 X-Service-Call
- **占位符扫描**：无 TBD/TODO；两处"以实际为准"注记为对既有代码的核实指引（LoginDto 字段、ArticleBaseBizService 构造依赖），非未定义内容
