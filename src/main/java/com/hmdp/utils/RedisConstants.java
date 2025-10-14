package com.hmdp.utils;

public class RedisConstants {
    // 命名规则：业务名:变量名:
    // 短信登录功能的常量
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final String LOGIN_USER_KEY = "login:user:";

    // 商铺缓存用到的常量
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TIME = 30L;
    public static final Long CACHE_NULL_TIME = 2L;
    public static final String CACHE_LOCK_KEY = "cache:lock:";
    public static final Long CACHE_LOCK_TIME = 2L;

    // 商铺类型缓存用到的常量
    public static final String CACHE_TYPELIST_KEY =  "cache:typeList";

}
