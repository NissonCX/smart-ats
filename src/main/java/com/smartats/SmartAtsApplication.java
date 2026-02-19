package com.smartats;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartATS 应用启动类
 *
 * @SpringBootApplication 注解说明：
 * = @Configuration: 标识为配置类（相当于 xml 配置文件）
 * = @EnableAutoConfiguration: 自动配置（根据依赖自动装配 Bean）
 * = @ComponentScan: 扫描当前包及其子包下的所有组件
 * 📌 为什么叫 main 方法？
 *    Java 程序的入口点，JVM 从这里开始执行
 * 📌 为什么要单独一个启动类？
 *    1. 集中管理启动逻辑
 *    2. 作为包扫描的起点（只扫描 com.smartats 下的类）
 *    3. 分离配置和业务代码
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.smartats.module.*.mapper")
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
