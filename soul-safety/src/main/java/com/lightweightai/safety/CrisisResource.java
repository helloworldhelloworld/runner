package com.lightweightai.safety;

/**
 * Crisis hotline or support resource.
 */
public record CrisisResource(
    String name,
    String phone,
    String description
) {
    /** Built-in crisis resources for China. */
    public static final java.util.List<CrisisResource> DEFAULTS = java.util.List.of(
        new CrisisResource("北京心理危机研究与干预中心", "010-82951332", "24小时心理危机干预热线"),
        new CrisisResource("全国心理援助热线", "400-161-9995", "全国统一心理援助热线"),
        new CrisisResource("生命热线", "400-821-1215", "24小时危机干预"),
        new CrisisResource("希望24热线", "400-161-9995", "危机干预与情感支持"),
        new CrisisResource("北京大学第六医院", "010-82801984", "精神科急诊")
    );
}
