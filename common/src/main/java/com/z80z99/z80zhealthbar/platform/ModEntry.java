package com.z80z99.z80zhealthbar.platform;

/**
 * 已安装模组条目（modId + 展示名）。
 * <p>展示名 = MOD 菜单里显示的名字（可含空格/中文）;modId = 内部技术标识（isModLoaded 匹配用）。
 * 自定义数据源的「目标模组」选择列表用它渲染。
 */
public record ModEntry(String id, String displayName) {
}
