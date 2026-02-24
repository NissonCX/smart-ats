package com.smartats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartATS 应用启动类
 * <p>
 * @MapperScan 已移至 {@link com.smartats.config.MyBatisPlusConfig}，
 * 避免 @WebMvcTest 切片测试加载 Mapper Bean。
 */
@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
@EnableScheduling
public class SmartAtsApplication {

    public static void main(String[] args) {
        /*
         * SpringApplication.run() 做了什么？
         * 1. 创建 Spring ApplicationContext（容器）
         * 2. 扫描 @Component、@Service、@Controller 等注解
         * 3. 启动嵌入式 Tomcat 服务器
         * 4. 注册所有自动配置的 Bean
         */
        SpringApplication.run(SmartAtsApplication.class, args);

        System.out.println("""

                ╔════════════════════════════════════════╗
                ║       🎉 SmartATS 启动成功！              ║
                ║                                          ║
                ║   访问地址: http://localhost:8080        ║
                ║   数据库:   MySQL @ 3307                 ║
                ║   缓存:     Redis @ 6379                 ║
                ║   消息队列: RabbitMQ @ 5672             ║
                ╚════════════════════════════════════════╝

                """);
    }
}
