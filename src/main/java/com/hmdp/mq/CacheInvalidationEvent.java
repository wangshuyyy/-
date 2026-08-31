package com.hmdp.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationEvent implements Serializable {

    public static final String SHOP = "shop";
    public static final String VOUCHER_LIST = "voucher-list";

    private String cacheType;
    private Long businessId;
}
