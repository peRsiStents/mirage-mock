package com.miragemock.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SM2 密钥对生成结果（公钥返回明文，私钥仅在生成时返回一次）。
 */
@Data
@AllArgsConstructor
public class Sm2KeyResult {

    private String alias;
    private String algorithm;
    private String publicKey;
    private String privateKey;
}
