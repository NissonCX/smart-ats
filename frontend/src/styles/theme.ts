import type { ThemeConfig } from 'antd';

/**
 * SmartATS Design System
 * 现代专业配色 - Cyan 主色调
 */
const theme: ThemeConfig = {
  token: {
    // Color - Cyan 为主色，更现代清新的配色
    colorPrimary: '#06B6D4',
    colorPrimaryHover: '#0891B2',
    colorPrimaryActive: '#0E7490',
    colorSuccess: '#10B981',
    colorWarning: '#F59E0B',
    colorError: '#F43F5E',
    colorInfo: '#3B82F6',
    colorLink: '#06B6D4',
    colorLinkHover: '#0891B2',

    // Border & Radius
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 6,
    borderRadiusXS: 4,

    // Typography
    fontFamily:
      "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
    fontSize: 14,
    lineHeight: 1.6,

    // Background
    colorBgContainer: '#FFFFFF',
    colorBgLayout: '#F8FAFC',
    colorBgElevated: '#FFFFFF',
    colorBgSpotlight: 'rgba(6, 182, 212, 0.08)',

    // Border Colors
    colorBorder: 'rgba(0, 0, 0, 0.08)',
    colorBorderSecondary: 'rgba(0, 0, 0, 0.06)',
    colorSplit: 'rgba(0, 0, 0, 0.06)',

    // Text
    colorText: '#1E293B',
    colorTextSecondary: '#64748B',
    colorTextTertiary: '#94A3B8',
    colorTextQuaternary: '#CBD5E1',

    // Control
    controlHeight: 38,
    controlHeightLG: 44,
    controlHeightSM: 32,

    // Motion
    motionDurationFast: '0.15s',
    motionDurationMid: '0.25s',
    motionDurationSlow: '0.35s',
    motionEaseInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    motionEaseOut: 'cubic-bezier(0.16, 1, 0.3, 1)',

    // Shadow - 更柔和的阴影
    boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
    boxShadowSecondary: '0 2px 6px 0 rgba(0, 0, 0, 0.03), 0 1px 2px -1px rgba(0, 0, 0, 0.02)',
    boxShadowTertiary: '0 4px 12px 0 rgba(0, 0, 0, 0.04), 0 2px 4px -1px rgba(0, 0, 0, 0.02)',
  },
  components: {
    // Button
    Button: {
      primaryShadow: 'none',
      defaultShadow: 'none',
      borderRadius: 8,
      controlHeight: 38,
      controlHeightLG: 46,
      controlHeightSM: 32,
      fontWeight: 500,
      paddingInline: 20,
    },

    // Input
    Input: {
      borderRadius: 8,
      controlHeight: 38,
      controlHeightLG: 46,
      colorBorder: 'rgba(0, 0, 0, 0.08)',
      activeBorderColor: '#06B6D4',
      hoverBorderColor: '#06B6D4',
    },

    // Select
    Select: {
      borderRadius: 8,
      controlHeight: 38,
      optionActiveBg: 'rgba(6, 182, 212, 0.08)',
    },

    // Card
    Card: {
      borderRadiusLG: 12,
      paddingLG: 20,
      paddingMD: 16,
      paddingSM: 12,
    },

    // Table
    Table: {
      borderRadius: 12,
      headerBg: '#F8FAFC',
      headerColor: '#64748B',
      headerSplitColor: 'rgba(0, 0, 0, 0.06)',
      rowHoverBg: '#F8FAFC',
      borderColor: 'rgba(0, 0, 0, 0.06)',
      cellPaddingInline: 16,
      cellPaddingBlock: 12,
    },

    // Menu
    Menu: {
      darkItemBg: 'transparent',
      darkSubMenuItemBg: 'transparent',
      darkItemSelectedBg: 'rgba(6, 182, 212, 0.15)',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.06)',
      itemBorderRadius: 8,
      itemMarginInline: 6,
      darkItemColor: 'rgba(255, 255, 255, 0.7)',
      darkItemSelectedColor: '#FFFFFF',
      darkItemSelectedBg: 'rgba(6, 182, 212, 0.15)',
    },

    // Tag
    Tag: {
      borderRadiusSM: 6,
      borderRadiusLG: 8,
    },

    // Modal
    Modal: {
      borderRadiusLG: 16,
    },

    // Drawer
    Drawer: {
      paddingLG: 24,
    },

    // Tabs
    Tabs: {
      itemSelectedColor: '#06B6D4',
      inkBarColor: '#06B6D4',
      itemColor: '#64748B',
    },

    // Statistic
    Statistic: {
      contentFontSize: 28,
    },

    // Progress
    Progress: {
      colorSuccess: '#10B981',
      colorInfo: '#06B6D4',
      colorWarning: '#F59E0B',
    },

    // Upload
    Upload: {
      borderRadius: 8,
    },
  },
};

export default theme;
