package com.qtysoft.cms.service;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内容 Schema：驱动控制台表单与预览渲染。
 * 新增字段只需在此追加；前端无需改代码（SchemaField 按 type 动态渲染）。
 * type 支持：text / textarea / email / number / boolean / select / list / object
 */
@Component
public class ContentSchema {

    private static Map<String, Object> f(String key, String label, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        return m;
    }

    private static Map<String, Object> f(String key, String label, String type, boolean required, int maxLength) {
        Map<String, Object> m = f(key, label, type);
        m.put("required", required);
        m.put("maxLength", maxLength);
        return m;
    }

    private static Map<String, Object> section(String key, String label, List<Map<String, Object>> fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("fields", fields);
        return m;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSchema(String docKey) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("key", docKey);
        List<Map<String, Object>> sections = new ArrayList<>();
        switch (docKey) {
            case "site" -> sections.addAll(siteSections());
            case "home" -> sections.addAll(homeSections());
            case "product" -> sections.addAll(productSections());
            case "solutions" -> sections.addAll(solutionsSections());
            case "cases" -> sections.addAll(casesSections());
            case "about" -> sections.addAll(aboutSections());
            default -> { }
        }
        schema.put("sections", sections);
        return schema;
    }

    private List<Map<String, Object>> siteSections() {
        return List.of(
            section("brand", "品牌", List.of(
                f("name", "公司名称", "text", true, 60),
                f("shortName", "简称（Logo 用）", "text", false, 20),
                f("slogan", "Slogan", "text", false, 80),
                f("logoText", "Logo 文字", "text", false, 20),
                f("cta", "导航按钮文案", "text", false, 20)
            )),
            section("contact", "联系信息", List.of(
                f("email", "业务邮箱", "email", false, 80),
                f("address", "公司地址", "text", false, 120),
                f("formEmail", "预约表单收件邮箱", "email", true, 80)
            )),
            section("footer", "页脚", List.of(
                f("copyrightYear", "版权年份", "number", true, 4),
                f("icp", "ICP 备案号", "text", false, 40),
                f("police", "公网安备号", "text", false, 40)
            )),
            section("nav", "导航栏", List.of(
                listField("items", "导航项", "导航项",
                    List.of(f("label", "名称", "text", true, 20), f("href", "链接", "text", true, 60), f("key", "高亮键", "text", false, 20)))
            )),
            section("seo", "SEO 默认", List.of(
                f("title", "站点标题", "text", true, 80),
                f("description", "站点描述", "textarea", false, 200)
            ))
        );
    }

    private List<Map<String, Object>> homeSections() {
        Map<String, Object> hero = section("hero", "首屏 Hero", List.of(
            f("badge", "徽标文案", "text", false, 40),
            f("titleLine1", "主标题第一行", "text", true, 40),
            f("titleLine2", "主标题第二行", "text", true, 40),
            f("subtitle", "副标题", "textarea", false, 200),
            f("ctaPrimary", "主按钮文案", "text", false, 20),
            f("ctaSecondary", "次按钮文案", "text", false, 20),
            listField("trust", "信任标签", "标签", List.of(f("text", "文案", "text", true, 40)))
        ));
        Map<String, Object> stats = section("stats", "数据条", List.of(
            listField("items", "指标", "指标", List.of(
                f("value", "数值", "text", true, 12),
                f("label", "说明", "text", true, 20)
            ))
        ));
        Map<String, Object> pain = section("pain", "客户痛点", List.of(
            f("title", "区块标题", "text", true, 60),
            f("subtitle", "区块副标题", "textarea", false, 200),
            listField("items", "痛点", "痛点", List.of(
                f("title", "标题", "text", true, 40),
                f("before", "常见困境", "textarea", true, 120),
                f("after", "乾腾元解法", "textarea", true, 120)
            ))
        ));
        Map<String, Object> agents = section("agents", "Agent 矩阵", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "区块标题", "text", true, 60),
            f("subtitle", "区块副标题", "textarea", false, 200),
            listField("items", "Agent 卡片", "Agent", List.of(
                f("name", "名称", "text", true, 30),
                f("desc", "简介", "text", true, 80)
            ))
        ));
        Map<String, Object> steps = section("steps", "交付步骤", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "区块标题", "text", true, 60),
            f("subtitle", "区块副标题", "textarea", false, 200),
            listField("items", "步骤", "步骤", List.of(
                f("num", "序号", "text", false, 8),
                f("title", "名称", "text", true, 30),
                f("desc", "说明", "text", true, 80)
            ))
        ));
        Map<String, Object> architecture = section("architecture", "技术架构", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "区块标题", "text", true, 60),
            listField("layers", "架构层", "层", List.of(
                f("tag", "层标识", "text", false, 8),
                f("title", "层标题", "text", true, 30),
                f("sub", "层说明", "textarea", true, 120)
            ))
        ));
        Map<String, Object> testimonials = section("testimonials", "客户证言", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "区块标题", "text", true, 60),
            listField("items", "证言", "证言", List.of(
                f("quote", "引言", "textarea", true, 300),
                f("author", "作者", "text", false, 30),
                f("role", "职位", "text", false, 40)
            ))
        ));
        Map<String, Object> logos = section("logos", "Logo 墙", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            listField("items", "客户名", "客户", List.of(f("name", "名称", "text", true, 30)))
        ));
        return List.of(hero, stats, pain, agents, steps, architecture, testimonials, logos);
    }

    private List<Map<String, Object>> productSections() {
        Map<String, Object> hero = section("hero", "首屏 Hero", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "主标题", "text", true, 60),
            f("subtitle", "副标题", "textarea", false, 200),
            f("ctaText", "按钮文案", "text", false, 20),
            f("ctaHref", "按钮链接", "text", false, 60)
        ));
        Map<String, Object> modules = section("modules", "能力模块", List.of(
            listField("items", "能力模块", "模块", List.of(
                f("eyebrow", "小标题", "text", false, 40),
                f("title", "标题", "text", true, 40),
                f("desc", "描述", "textarea", true, 200),
                listField("tags", "标签", "标签", List.of(f("text", "文案", "text", true, 40)))
            ))
        ));
        return List.of(hero, modules);
    }

    private List<Map<String, Object>> solutionsSections() {
        Map<String, Object> hero = section("hero", "首屏 Hero", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "主标题", "text", true, 60),
            f("subtitle", "副标题", "textarea", false, 200)
        ));
        Map<String, Object> cards = section("industries", "行业方案", List.of(
            listField("items", "行业卡", "行业", List.of(
                f("tag", "行业标签", "text", true, 30),
                f("title", "标题", "text", true, 60),
                f("desc", "描述", "textarea", true, 200),
                listField("list", "能力列表", "能力", List.of(f("text", "文案", "text", true, 60)))
            ))
        ));
        return List.of(hero, cards);
    }

    private List<Map<String, Object>> casesSections() {
        Map<String, Object> hero = section("hero", "首屏 Hero", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "主标题", "text", true, 60),
            f("subtitle", "副标题", "textarea", false, 200)
        ));
        Map<String, Object> cards = section("cases", "案例", List.of(
            listField("items", "案例", "案例", List.of(
                f("label", "标签行（行业 / 客户）", "text", true, 40),
                f("title", "标题", "text", true, 60),
                f("desc", "描述", "textarea", true, 240),
                listField("stats", "量化指标", "指标", List.of(
                    f("value", "数值", "text", true, 12),
                    f("suffix", "单位后缀", "text", false, 6),
                    f("label", "说明", "text", true, 20)
                )),
                f("visualNum", "视觉大数字", "text", false, 12),
                f("visualLabel", "视觉说明", "text", false, 30),
                listField("tags", "标签", "标签", List.of(f("text", "文案", "text", true, 40)))
            ))
        ));
        return List.of(hero, cards);
    }

    private List<Map<String, Object>> aboutSections() {
        Map<String, Object> hero = section("hero", "首屏 Hero", List.of(
            f("eyebrow", "小标题", "text", false, 40),
            f("title", "主标题", "text", true, 60),
            f("subtitle", "副标题", "textarea", false, 200)
        ));
        Map<String, Object> intro = section("intro", "公司简介", List.of(
            f("title", "标题", "text", true, 60),
            f("body", "正文", "textarea", true, 400)
        ));
        Map<String, Object> stats = section("stats", "数据格", List.of(
            listField("items", "指标", "指标", List.of(
                f("value", "数值", "text", true, 12),
                f("suffix", "单位后缀", "text", false, 6),
                f("label", "说明", "text", true, 20)
            ))
        ));
        Map<String, Object> values = section("values", "价值观", List.of(
            listField("items", "价值项", "价值", List.of(
                f("num", "序号", "text", false, 8),
                f("name", "名称", "text", true, 30),
                f("desc", "说明", "text", true, 80)
            ))
        ));
        Map<String, Object> contact = section("contact", "联系区", List.of(
            f("title", "标题", "text", true, 60),
            f("desc", "说明", "textarea", false, 200)
        ));
        return List.of(hero, intro, stats, values, contact);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listField(String key, String label, String itemLabel, List<Map<String, Object>> itemFields) {
        Map<String, Object> m = f(key, label, "list");
        m.put("itemLabel", itemLabel);
        m.put("itemFields", itemFields);
        return m;
    }
}
