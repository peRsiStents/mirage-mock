package com.miragemock.dsl.func;

import com.miragemock.dsl.spi.FunctionDescriptor;

import java.util.Arrays;
import java.util.List;

/**
 * 函数市场目录：函数说明 / 示例，供管理端函数库页面与试算提示展示。
 */
public final class FunctionCatalog {

    private FunctionCatalog() {
    }

    public static List<FunctionDescriptor> catalog() {
        return Arrays.asList(
                // 人员与证件
                new FunctionDescriptor("name.cn", "人员与证件", "中文姓名", "${name.cn}", "string"),
                new FunctionDescriptor("name.en", "人员与证件", "英文姓名", "${name.en}", "string"),
                new FunctionDescriptor("phone.cn_mobile", "人员与证件", "中国大陆手机号", "${phone.cn_mobile}", "string"),
                new FunctionDescriptor("idcard.cn", "人员与证件", "身份证号（含校验位）", "${idcard.cn}", "string"),
                new FunctionDescriptor("idcard.birthdate", "人员与证件", "身份证对应出生日期（复用同行 idcard.cn 的证）", "${idcard.birthdate} / ${idcard.birthdate('yyyy/MM/dd')}", "string"),
                new FunctionDescriptor("idcard.gender", "人员与证件", "身份证对应性别（男/女）", "${idcard.gender}", "string"),
                new FunctionDescriptor("bankcard.cn", "人员与证件", "银行卡号（Luhn 校验）", "${bankcard.cn}", "string"),
                new FunctionDescriptor("uscc.cn", "人员与证件", "统一社会信用代码", "${uscc.cn}", "string"),
                new FunctionDescriptor("address.cn", "人员与证件", "中文地址", "${address.cn}", "string"),
                new FunctionDescriptor("email", "人员与证件", "邮箱", "${email}", "string"),

                // 数值与序列
                new FunctionDescriptor("int", "数值与时间", "区间随机整数", "${int(1, 100)}", "long"),
                new FunctionDescriptor("decimal", "数值与时间", "区间随机小数（金额）", "${decimal(1000, 9999, 2)}", "string"),
                new FunctionDescriptor("seq", "数值与时间", "项目级自增序列（持久）", "${seq('orderNo', 1000)}", "long"),
                new FunctionDescriptor("uuid", "数值与时间", "UUID", "${uuid} / ${uuid('nodash')}", "string"),

                // 字符串
                new FunctionDescriptor("string", "字符串", "定长/区间随机字符串，charset: alpha/numeric/alpha_num/hex", "${string(alpha_num, 32)}", "string"),
                new FunctionDescriptor("regex", "字符串", "按正则生成字符串", "${regex('^ORD\\\\d{6}$')}", "string"),
                new FunctionDescriptor("enum", "字符串", "枚举随机取一", "${enum('a','b','c')}", "string"),
                new FunctionDescriptor("concat", "字符串", "字符串拼接", "${concat('ORD', ${string(numeric,6)})}", "string"),
                new FunctionDescriptor("repeat", "字符串", "生成 n 元素数组（在叶子节点使用）", "${repeat(3, { orderNo: ${string(numeric,6)} })}", "array"),

                // 时间
                new FunctionDescriptor("date", "时间", "随机日期，支持 now/now-30d", "${date(now-30d, now, yyyy-MM-dd)}", "string"),
                new FunctionDescriptor("datetime", "时间", "随机日期时间", "${datetime(now-30d, now, yyyy-MM-dd HH:mm:ss)}", "string"),

                // 摘要与编码
                new FunctionDescriptor("md5", "摘要与编码", "MD5 摘要（hex）", "${md5(${data.phone})}", "string"),
                new FunctionDescriptor("sha256", "摘要与编码", "SHA-256 摘要（hex）", "${sha256(${data.phone})}", "string"),
                new FunctionDescriptor("sm3", "摘要与编码", "SM3 国密摘要（hex）", "${sm3(${field.serialNo})}", "string"),
                new FunctionDescriptor("base64_encode", "摘要与编码", "Base64 编码", "${base64_encode(${data.phone})}", "string"),
                new FunctionDescriptor("hex_encode", "摘要与编码", "Hex 编码", "${hex_encode(${data.phone})}", "string"),
                new FunctionDescriptor("url_encode", "摘要与编码", "URL 编码", "${url_encode(${data.phone})}", "string"),

                // 加解密 / 签名
                new FunctionDescriptor("sm4_encrypt", "加解密签名", "SM4 加密（默认 ECB/Base64，可 'CBC'）", "${sm4_encrypt(${data.phone}, 'key_alias', 'CBC')}", "string"),
                new FunctionDescriptor("sm4_decrypt", "加解密签名", "SM4 解密（默认 ECB/Base64 输入，可 'CBC'）", "${sm4_decrypt(${field.cipher}, 'key_alias')}", "string"),
                new FunctionDescriptor("sm2_sign", "加解密签名", "SM2 签名", "${sm2_sign(${field.serialNo}, 'key_gateway')}", "string"),
                new FunctionDescriptor("sm2_verify", "加解密签名", "SM2 验签（原文, 签名, 公钥别名）", "${sm2_verify(${field.data}, ${field.sign}, 'key_gateway')}", "string"),
                new FunctionDescriptor("sm2_encrypt", "加解密签名", "SM2 加密", "${sm2_encrypt(${data.phone}, 'key_alias')}", "string"),
                new FunctionDescriptor("sm2_decrypt", "加解密签名", "SM2 解密", "${sm2_decrypt(${field.cipher}, 'key_alias')}", "string"),
                new FunctionDescriptor("aes_encrypt", "加解密签名", "AES 加密", "${aes_encrypt(${data.phone}, 'key_alias')}", "string"),
                new FunctionDescriptor("aes_decrypt", "加解密签名", "AES 解密", "${aes_decrypt(${field.cipher}, 'key_alias')}", "string"),
                new FunctionDescriptor("rsa_sign", "加解密签名", "RSA(SHA256) 签名", "${rsa_sign(${data.phone}, 'key_alias')}", "string"),
                new FunctionDescriptor("rsa_encrypt", "加解密签名", "RSA 加密", "${rsa_encrypt(${data.phone}, 'key_alias')}", "string"),

                // 上下文变量
                new FunctionDescriptor("path.*", "上下文变量", "HTTP 路径变量", "${path.userId}", "any"),
                new FunctionDescriptor("field.*", "上下文变量", "TCP 报文字段 / HTTP请求体字段 / 已渲染字段引用", "${field.serialNo}", "any"),
                new FunctionDescriptor("count", "上下文变量", "文件生成：数据行数（=生成行数，可用于首行汇总笔数）", "${count}", "long")
        );
    }
}
