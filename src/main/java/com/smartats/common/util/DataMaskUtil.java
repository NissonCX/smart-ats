package com.smartats.common.util;

/**
 * 数据脱敏工具类
 * <p>
 * 用于对手机号、邮箱等敏感字段做展示层脱敏处理。
 * 脱敏结果仅用于前端展示，<b>不写入数据库</b>。
 */
public class DataMaskUtil {

    private DataMaskUtil() {}

    /**
     * 手机号脱敏：保留前 3 位 + 后 4 位，中间替换为 ****
     * <p>
     * 示例：13812345678 → 138****5678
     *
     * @param phone 原始手机号，允许为 null
     * @return 脱敏后的手机号；null 或长度 < 7 时原样返回
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：@ 前保留前 2 位，中间替换为 ****，@ 后完整保留
     * <p>
     * 示例：zhangsan@example.com → zh****@example.com
     *
     * @param email 原始邮箱，允许为 null
     * @return 脱敏后的邮箱；null 或格式不含 @ 时原样返回
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex); // 含 @

        if (local.length() <= 2) {
            return local + "****" + domain;
        }
        return local.substring(0, 2) + "****" + domain;
    }
}
