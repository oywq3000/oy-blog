package com.oyproj.common.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * IP地址实体类
 */

@Data
@AllArgsConstructor
public class IpDomain {

    private Boolean cdnIP;

    private Boolean xcdnIP;

    private String isp;

    private String region;

}