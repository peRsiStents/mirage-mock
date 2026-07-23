package com.miragemock.dsl.func;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 仿真数据生成：中文/英文姓名、手机号、身份证、银行卡、统一社会信用代码、地址、邮箱。
 * 校验位（身份证 GB11643、银行卡 Luhn、USCC GB32100）自研以保证合法。
 */
public final class FakerData {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String DIGITS = "0123456789";

    private static final String[] SURNAMES = {
            "赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈",
            "褚", "卫", "蒋", "沈", "韩", "杨", "朱", "秦", "尤", "许",
            "何", "吕", "施", "张", "孔", "曹", "严", "华", "金", "魏",
            "陶", "姜", "戚", "谢", "邹", "喻", "柏", "水", "窦", "章",
            "云", "苏", "潘", "葛", "奚", "范", "彭", "郎", "鲁", "韦",
            "昌", "马", "苗", "凤", "花", "方", "俞", "任", "袁", "柳",
            "唐", "罗", "薛", "雷", "贺", "倪", "汤", "滕", "殷", "罗",
            "邓", "曾", "黄", "梁", "宋", "许", "韩", "冯", "邓", "曹"
    };

    private static final String[] GIVEN_CHARS = {
            "伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", "洋",
            "勇", "艳", "杰", "娟", "涛", "明", "超", "秀", "霞", "平",
            "刚", "桂", "英", "华", "斌", "鹏", "宇", "俊杰", "浩", "轩",
            "梓", "涵", "欣", "怡", "佳", "婷", "雪", "晨", "宁", "妍",
            "博", "凯", "远", "航", "嘉", "睿", "乐", "天", "思", "梦"
    };

    private static final String[] EN_FIRST = {
            "James", "John", "Robert", "Michael", "William", "David", "Joseph", "Charles",
            "Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Susan", "Jessica", "Sarah"
    };

    private static final String[] EN_LAST = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Wilson", "Anderson", "Taylor", "Thomas", "Moore", "Lee"
    };

    private static final String[] MOBILE_PREFIX = {
            "133", "149", "153", "173", "177", "180", "181", "189", "199",
            "130", "131", "132", "145", "155", "156", "166", "175", "176", "185", "186", "196",
            "134", "135", "136", "137", "138", "139", "147", "150", "151", "152", "158", "159",
            "172", "178", "182", "183", "184", "187", "188", "195", "197", "198"
    };

    private static final String[] IDCARD_REGION = {
            "110101", "110105", "120101", "310115", "310104", "440304", "440106",
            "330102", "330106", "320105", "320104", "510104", "510107", "420102",
            "610104", "500103", "370102", "210102", "350202", "450102"
    };

    private static final String[] BANK_BINS = {
            "622202", "622848", "621700", "622588", "621662", "622700", "622609",
            "621558", "622150", "621283", "622521", "622690"
    };

    private static final String[] ADDRESS_PREFIX = {
            "北京市", "上海市", "广东省深圳市", "浙江省杭州市", "江苏省南京市",
            "四川省成都市", "湖北省武汉市", "陕西省西安市", "重庆市", "山东省济南市",
            "辽宁省沈阳市", "福建省厦门市", "广西壮族自治区南宁市"
    };

    private static final String[] ADDRESS_STREET = {
            "中山路", "解放路", "人民大道", "建国路", "和平路", "建设大道", "学府路", "科技路", "滨海大道", "迎宾路"
    };

    private static final String[] EMAIL_DOMAIN = {
            "example.com", "test.cn", "mock.io", "126.com", "163.com", "qq.com"
    };

    private FakerData() {
    }

    // ============ 姓名 ============

    public static String cnName() {
        String surname = pick(SURNAMES);
        int len = RNG.nextBoolean() ? 1 : 2;
        StringBuilder sb = new StringBuilder(surname);
        for (int i = 0; i < len; i++) {
            String g = pick(GIVEN_CHARS);
            sb.append(g.charAt(RNG.nextInt(g.length())));
        }
        return sb.toString();
    }

    public static String enName() {
        return pick(EN_FIRST) + " " + pick(EN_LAST);
    }

    public static String email() {
        StringBuilder local = new StringBuilder();
        int len = RNG.nextInt(6) + 4;
        String alpha = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < len; i++) {
            local.append(alpha.charAt(RNG.nextInt(alpha.length())));
        }
        return local.toString() + "@" + pick(EMAIL_DOMAIN);
    }

    // ============ 手机号 ============

    public static String mobile() {
        StringBuilder sb = new StringBuilder(pick(MOBILE_PREFIX));
        for (int i = 0; i < 8; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    // ============ 身份证号（GB11643 校验位）============

    private static final int[] IDCARD_WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] IDCARD_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    public static String idcard() {
        String region = pick(IDCARD_REGION);
        LocalDate birth = randomBirth();
        String birthStr = String.format("%04d%02d%02d", birth.getYear(), birth.getMonthValue(), birth.getDayOfMonth());
        StringBuilder body = new StringBuilder(region).append(birthStr);
        for (int i = 0; i < 3; i++) {
            body.append(RNG.nextInt(10));
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (body.charAt(i) - '0') * IDCARD_WEIGHT[i];
        }
        body.append(IDCARD_CHECK[sum % 11]);
        return body.toString();
    }

    private static LocalDate randomBirth() {
        long now = LocalDate.now(ZoneId.of("Asia/Shanghai")).toEpochDay();
        long min = LocalDate.of(1950, 1, 1).toEpochDay();
        long day = min + (long) (RNG.nextDouble() * (now - min));
        return LocalDate.ofEpochDay(day);
    }

    // ============ 银行卡号（Luhn 校验）============

    public static String bankcard() {
        StringBuilder sb = new StringBuilder(pick(BANK_BINS));
        int targetLen = 16 + RNG.nextInt(3) * 3; // 16/19
        while (sb.length() < targetLen - 1) {
            sb.append(RNG.nextInt(10));
        }
        // 计算 Luhn 校验位
        int sum = 0;
        boolean odd = true;
        for (int i = sb.length() - 1; i >= 0; i--) {
            int d = sb.charAt(i) - '0';
            if (odd) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            odd = !odd;
        }
        int check = (10 - sum % 10) % 10;
        sb.append(check);
        return sb.toString();
    }

    // ============ 统一社会信用代码（GB32100 校验位）============

    // USCC 字符集：0-9 + A-Z 去除 I O S V Z（共 31 个）
    private static final String USCC_ALPHABET = "0123456789ABCDEFGHJKLMNPQRTUWXY";
    private static final int[] USCC_WEIGHT = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};

    public static String uscc() {
        // 登记管理部门 1 位（1/5/9 等）+ 机构类别 1 位
        char dept = DIGITS.charAt(1 + RNG.nextInt(8));
        char type = DIGITS.charAt(RNG.nextInt(9) + 1);
        StringBuilder body = new StringBuilder().append(dept).append(type);
        // 行政区划 6 位
        body.append(pick(IDCARD_REGION).substring(0, 6));
        // 主体标识 9 位（数字/字母）
        while (body.length() < 17) {
            body.append(USCC_ALPHABET.charAt(RNG.nextInt(USCC_ALPHABET.length())));
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += USCC_ALPHABET.indexOf(body.charAt(i)) * USCC_WEIGHT[i];
        }
        int check = 31 - sum % 31;
        if (check == 31) {
            check = 0;
        }
        body.append(USCC_ALPHABET.charAt(check));
        return body.toString();
    }

    // ============ 地址 ============

    public static String address() {
        return pick(ADDRESS_PREFIX) + pick(ADDRESS_STREET) + (RNG.nextInt(900) + 100) + "号";
    }

    // ============ 工具 ============

    public static String randomString(String charset, int minLen, int maxLen) {
        int len = minLen == maxLen ? minLen : minLen + RNG.nextInt(maxLen - minLen + 1);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(charset.charAt(RNG.nextInt(charset.length())));
        }
        return sb.toString();
    }

    public static int randomInt(int min, int max) {
        if (max <= min) {
            return min;
        }
        return RNG.nextInt(max - min + 1) + min;
    }

    public static <T> T pick(T[] arr) {
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }
}
