package com.miragemock.admin.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.ProjectMapper;
import com.miragemock.admin.mapper.ProjectMemberMapper;
import com.miragemock.admin.mapper.SecretKeyMapper;
import com.miragemock.admin.mapper.TcpListenerMapper;
import com.miragemock.admin.mapper.UserAccountMapper;
import com.miragemock.admin.service.KeyService;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.Project;
import com.miragemock.common.entity.ProjectMember;
import com.miragemock.common.entity.SecretKey;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.entity.UserAccount;
import com.miragemock.core.cache.RuleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动数据初始化：引导管理员账号与示例项目，便于开箱即用。
 */
@Component
@Order(100)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String GRAY_TEMPLATE =
            "{\"status\":200,\"headers\":{\"X-Mock-Rule\":\"gray\"},\"body\":{\"code\":\"0000\",\"message\":\"success\","
                    + "\"data\":{\"userId\":\"${path.userId}\",\"level\":\"VIP\",\"userName\":\"${name.cn}\","
                    + "\"phone\":\"${phone.cn_mobile}\",\"idCard\":\"${idcard.cn}\","
                    + "\"balance\":\"${decimal(1000,99999,2)}\",\"sign\":\"${sm3(${data.phone})}\"}}}";

    private static final String DEFAULT_TEMPLATE =
            "{\"status\":200,\"body\":{\"code\":\"0000\",\"message\":\"success\","
                    + "\"data\":{\"userId\":\"${path.userId}\",\"level\":\"NORMAL\","
                    + "\"userName\":\"${name.cn}\",\"phone\":\"${phone.cn_mobile}\"}}}";

    private final UserAccountMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final ProjectMemberMapper memberMapper;
    private final TcpListenerMapper listenerMapper;
    private final SecretKeyMapper secretKeyMapper;
    private final KeyService keyService;
    private final PasswordEncoder passwordEncoder;
    private final RuleCache ruleCache;

    @Autowired
    public DataInitializer(UserAccountMapper userMapper, ProjectMapper projectMapper,
                           ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper,
                           ProjectMemberMapper memberMapper, TcpListenerMapper listenerMapper,
                           SecretKeyMapper secretKeyMapper, KeyService keyService,
                           PasswordEncoder passwordEncoder, RuleCache ruleCache) {
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.memberMapper = memberMapper;
        this.listenerMapper = listenerMapper;
        this.secretKeyMapper = secretKeyMapper;
        this.keyService = keyService;
        this.passwordEncoder = passwordEncoder;
        this.ruleCache = ruleCache;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        Project demo = seedDemo();
        if (demo != null) {
            seedTcpDemo(demo);
        }
        ruleCache.reloadAll();
    }

    private void seedAdmin() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNickname("管理员");
        admin.setIsAdmin(1);
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("已创建默认管理员账号: admin / admin123");
    }

    private Project seedDemo() {
        Project existing = projectMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getCode, "demo"));
        if (existing != null) {
            return existing;
        }
        Project project = new Project();
        project.setName("示例项目");
        project.setCode("demo");
        project.setStatus(Constants.STATUS_ENABLED);
        project.setRuleVersion(Constants.INITIAL_RULE_VERSION);
        projectMapper.insert(project);

        ApiInterface iface = new ApiInterface();
        iface.setProjectId(project.getId());
        iface.setName("查询用户信息");
        iface.setProtocol("HTTP");
        iface.setHttpMethod("GET");
        iface.setHttpPath("/api/user/{userId}");
        iface.setStatus(Constants.STATUS_ENABLED);
        interfaceMapper.insert(iface);

        MockRule gray = new MockRule();
        gray.setInterfaceId(iface.getId());
        gray.setName("灰度用户");
        gray.setPriority(10);
        gray.setMatchCondition("[{\"source\":\"header\",\"key\":\"X-Env\",\"op\":\"eq\",\"value\":\"gray\"}]");
        gray.setResponseTemplate(GRAY_TEMPLATE);
        gray.setDelayType("NONE");
        gray.setFaultType("NONE");
        gray.setStatus(Constants.STATUS_ENABLED);
        ruleMapper.insert(gray);

        MockRule def = new MockRule();
        def.setInterfaceId(iface.getId());
        def.setName("兜底");
        def.setPriority(100);
        def.setMatchCondition("[]");
        def.setResponseTemplate(DEFAULT_TEMPLATE);
        def.setDelayType("NONE");
        def.setFaultType("NONE");
        def.setStatus(Constants.STATUS_ENABLED);
        ruleMapper.insert(def);

        // 管理员加入示例项目
        UserAccount admin = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, "admin"));
        if (admin != null) {
            ProjectMember m = new ProjectMember();
            m.setProjectId(project.getId());
            m.setUserId(admin.getId());
            m.setMemberRole("ADMIN");
            memberMapper.insert(m);
        }
        log.info("已创建示例项目 demo，接口 GET /api/user/{userId}（含灰度/兜底两条规则），Mock 端口见配置");
        return project;
    }

    private void seedTcpDemo(Project demo) {
        // 1) 确保有 SM2 密钥 key_gateway
        Long keyCount = secretKeyMapper.selectCount(new LambdaQueryWrapper<SecretKey>()
                .eq(SecretKey::getProjectId, demo.getId())
                .eq(SecretKey::getAlias, "key_gateway"));
        if (keyCount == null || keyCount == 0) {
            keyService.generateSm2(demo.getId(), "key_gateway");
        }

        // 2) TCP 监听器：长度头 4B 大端 + JSON，长连接异步
        TcpListener listener = listenerMapper.selectOne(new LambdaQueryWrapper<TcpListener>()
                .eq(TcpListener::getProjectId, demo.getId())
                .eq(TcpListener::getName, "demo-tcp"));
        if (listener == null) {
            listener = new TcpListener();
            listener.setProjectId(demo.getId());
            listener.setName("demo-tcp");
            listener.setPort(9001);
            listener.setConnMode("LONG");
            listener.setMatchMode("ASYNC");
            listener.setFrameConfig("{\"type\":\"length_field\",\"lenBytes\":4,\"endian\":\"big\",\"offset\":0,\"adjustment\":0,\"initialStrip\":4}");
            listener.setMessageFormat("json");
            listener.setRouteExtract("$.transCode");
            listener.setSerialExtract("$.serialNo");
            listener.setStatus(Constants.STATUS_ENABLED);
            listenerMapper.insert(listener);
        }

        // 3) TCP 接口：路由交易码 0200
        ApiInterface iface = interfaceMapper.selectOne(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, demo.getId())
                .eq(ApiInterface::getTcpListenerId, listener.getId()));
        if (iface == null) {
            iface = new ApiInterface();
            iface.setProjectId(demo.getId());
            iface.setName("消费交易");
            iface.setProtocol("TCP");
            iface.setTcpListenerId(listener.getId());
            iface.setTcpRouteExpr("0200");
            iface.setStatus(Constants.STATUS_ENABLED);
            interfaceMapper.insert(iface);
        }

        // 4) 规则：金额 <= 50000 成功，响应带 SM3 MAC 与 SM2 签名
        Long ruleCount = ruleMapper.selectCount(new LambdaQueryWrapper<MockRule>().eq(MockRule::getInterfaceId, iface.getId()));
        if (ruleCount == null || ruleCount == 0) {
            String tpl = "{\"transCode\":\"0210\",\"serialNo\":\"${field.serialNo}\",\"respCode\":\"00\","
                    + "\"cardNo\":\"${bankcard.cn}\",\"amount\":\"${field.amount}\",\"traceNo\":\"${string(numeric,12)}\","
                    + "\"mac\":\"${sm3(${field.serialNo} + ${field.amount})}\","
                    + "\"sign\":\"${sm2_sign(${field.serialNo} + '|' + ${field.amount}, 'key_gateway')}\"}";
            MockRule rule = new MockRule();
            rule.setInterfaceId(iface.getId());
            rule.setName("消费成功");
            rule.setPriority(10);
            rule.setMatchCondition("[{\"source\":\"field\",\"key\":\"$.amount\",\"op\":\"lte\",\"value\":50000}]");
            rule.setResponseTemplate(tpl);
            rule.setDelayType("NONE");
            rule.setFaultType("NONE");
            rule.setStatus(Constants.STATUS_ENABLED);
            ruleMapper.insert(rule);
        }
        log.info("已创建示例 TCP 监听器 demo-tcp（端口 9001，交易码 0200）");
    }
}
