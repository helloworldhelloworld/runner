package com.lightweightai.safety;

import java.util.Arrays;
import java.util.List;

/**
 * Crisis hotline or support resource.
 */
public class CrisisResource {

    private final String name;
    private final String phone;
    private final String description;

    public CrisisResource(String name, String phone, String description) {
        this.name = name;
        this.phone = phone;
        this.description = description;
    }

    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getDescription() { return description; }

    /** Built-in crisis resources for China. */
    public static final List<CrisisResource> DEFAULTS = Arrays.asList(
        new CrisisResource("北京心理危机研究与干预中心", "010-82951332", "24小时心理危机干预热线"),
        new CrisisResource("全国心理援助热线", "400-161-9995", "全国统一心理援助热线"),
        new CrisisResource("生命热线", "400-821-1215", "24小时危机干预"),
        new CrisisResource("希望24热线", "400-161-9995", "危机干预与情感支持"),
        new CrisisResource("北京大学第六医院", "010-82801984", "精神科急诊")
    );
}
