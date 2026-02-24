import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Card,
  Space,
  Tag,
  Select,
  DatePicker,
  Typography,
  Statistic,
  Badge,
  Tooltip,
  Spin,
  Empty,
  message,
} from 'antd';
import {
  TeamOutlined,
  FileTextOutlined,
  TrophyOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  RiseOutlined,
  LinkOutlined,
  DisconnectOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { motion } from 'framer-motion';
import dayjs from 'dayjs';
import { analyticsApi, getAnalyticsSseUrl } from '../../api';
import type { RecruitmentOverviewDTO, AnalyticsQueryRequest, FunnelStageDTO } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';
import { useSse } from '../../hooks/useSse';

const { Text } = Typography;
const { RangePicker } = DatePicker;

const stageColors: Record<string, string> = {
  PENDING: '#8b5cf6',
  SCREENING: '#3b82f6',
  INTERVIEW: '#f59e0b',
  OFFER: '#10b981',
  REJECTED: '#ef4444',
  WITHDRAWN: '#6b7280',
};

export default function AnalyticsPage() {
  const [overview, setOverview] = useState<RecruitmentOverviewDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<AnalyticsQueryRequest>({});

  // ---------- SSE real-time updates ----------
  const sseUrl = useMemo(() => getAnalyticsSseUrl(), []);
  const { status: sseStatus, reconnect: sseReconnect } = useSse({
    url: sseUrl,
    onMessage: useCallback(() => {
      // When a pipeline change event arrives, reload the overview
      loadOverview();
    }, []),
    onOpen: useCallback(() => {
      message.success({ content: '实时推送已连接', key: 'sse', duration: 2 });
    }, []),
  });

  // Use a ref-based approach to ensure loadOverview is stable for SSE callback
  const loadOverview = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await analyticsApi.getOverview(query);
      if (data.data) {
        setOverview(data.data);
      }
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  const handleDateChange = (_: unknown, dateStrings: [string, string]) => {
    setQuery((q) => ({
      ...q,
      startDate: dateStrings[0] || undefined,
      endDate: dateStrings[1] || undefined,
    }));
  };

  // ---------- ECharts: Funnel ----------
  const funnelOption = useMemo(() => {
    if (!overview?.funnel?.length) return null;
    const funnelData = overview.funnel
      .filter((s) => s.stage !== 'WITHDRAWN')
      .map((s: FunnelStageDTO) => ({
        name: s.stageLabel,
        value: s.count,
      }));

    return {
      tooltip: {
        trigger: 'item',
        formatter: (params: { name: string; value: number; percent: number }) =>
          `${params.name}<br/>数量: <b>${params.value}</b><br/>占比: <b>${params.percent}%</b>`,
      },
      color: ['#8b5cf6', '#3b82f6', '#f59e0b', '#10b981', '#ef4444'],
      series: [
        {
          type: 'funnel',
          left: '10%',
          top: 10,
          bottom: 10,
          width: '80%',
          min: 0,
          max: Math.max(...funnelData.map((d: { value: number }) => d.value), 1),
          minSize: '10%',
          maxSize: '100%',
          sort: 'none',
          gap: 4,
          label: {
            show: true,
            position: 'inside',
            formatter: '{b}\n{c}',
            fontSize: 13,
            color: '#fff',
          },
          emphasis: {
            label: { fontSize: 15 },
          },
          itemStyle: {
            borderColor: '#fff',
            borderWidth: 2,
            borderRadius: 4,
          },
          data: funnelData,
        },
      ],
    };
  }, [overview?.funnel]);

  // ---------- ECharts: Monthly Trend ----------
  const trendOption = useMemo(() => {
    if (!overview?.applicationTrend) return null;
    const entries = Object.entries(overview.applicationTrend).sort(
      ([a], [b]) => a.localeCompare(b)
    );
    if (entries.length === 0) return null;

    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
      },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'category',
        data: entries.map(([k]) => k),
        axisLabel: { fontSize: 11, color: '#6b7280' },
        axisLine: { lineStyle: { color: '#e5e7eb' } },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f3f4f6' } },
        axisLabel: { fontSize: 11, color: '#9ca3af' },
      },
      series: [
        {
          type: 'bar',
          data: entries.map(([, v]) => v),
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#818cf8' },
                { offset: 1, color: '#6366f1' },
              ],
            },
          },
          barMaxWidth: 36,
        },
      ],
    };
  }, [overview?.applicationTrend]);

  // ---------- Conversion Rate Bar ----------
  const conversionOption = useMemo(() => {
    if (!overview?.funnel?.length) return null;
    const data = overview.funnel.filter((s) => s.stage !== 'WITHDRAWN');
    return {
      tooltip: {
        trigger: 'axis',
        formatter: (params: Array<{ name: string; value: number }>) =>
          `${params[0].name}<br/>转化率: <b>${params[0].value}%</b>`,
      },
      grid: { left: 80, right: 20, top: 10, bottom: 30 },
      xAxis: {
        type: 'value',
        max: 100,
        axisLabel: { formatter: '{value}%', fontSize: 11, color: '#9ca3af' },
        splitLine: { lineStyle: { color: '#f3f4f6' } },
      },
      yAxis: {
        type: 'category',
        data: data.map((s: FunnelStageDTO) => s.stageLabel).reverse(),
        axisLabel: { fontSize: 12, color: '#374151' },
        axisLine: { show: false },
        axisTick: { show: false },
      },
      series: [
        {
          type: 'bar',
          data: data
            .map((s: FunnelStageDTO) => ({
              value: Math.round(s.conversionRate * 10) / 10,
              itemStyle: { color: stageColors[s.stage] || '#6b7280' },
            }))
            .reverse(),
          barMaxWidth: 20,
          itemStyle: { borderRadius: [0, 4, 4, 0] },
          label: {
            show: true,
            position: 'right',
            formatter: '{c}%',
            fontSize: 11,
            color: '#6b7280',
          },
        },
      ],
    };
  }, [overview?.funnel]);

  // ---------- KPI Cards Data ----------
  const kpiCards = useMemo(() => {
    if (!overview) return [];
    return [
      {
        title: '总申请数',
        value: overview.totalApplications,
        icon: <FileTextOutlined />,
        color: '#6366f1',
        bg: 'bg-indigo-50',
      },
      {
        title: '候选人数',
        value: overview.totalCandidates,
        icon: <TeamOutlined />,
        color: '#3b82f6',
        bg: 'bg-blue-50',
      },
      {
        title: '开放职位',
        value: overview.openJobs,
        suffix: ` / ${overview.totalJobs}`,
        icon: <RiseOutlined />,
        color: '#8b5cf6',
        bg: 'bg-purple-50',
      },
      {
        title: '面试安排',
        value: overview.interviewsScheduled,
        icon: <ClockCircleOutlined />,
        color: '#f59e0b',
        bg: 'bg-amber-50',
      },
      {
        title: 'Offer 发出',
        value: overview.offersExtended,
        icon: <TrophyOutlined />,
        color: '#10b981',
        bg: 'bg-emerald-50',
      },
      {
        title: 'Offer 转化率',
        value: overview.offerRate,
        suffix: '%',
        precision: 1,
        icon: <ThunderboltOutlined />,
        color: '#ec4899',
        bg: 'bg-pink-50',
      },
    ];
  }, [overview]);

  const sseStatusConfig = {
    connecting: { color: 'processing' as const, text: '连接中' },
    connected: { color: 'success' as const, text: '已连接' },
    disconnected: { color: 'default' as const, text: '已断开' },
    error: { color: 'error' as const, text: '连接失败' },
  };

  return (
    <PageTransition>
      <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <h2 className="text-xl font-semibold text-gray-900 m-0">招聘分析</h2>
          <Tooltip
            title={
              sseStatus === 'connected'
                ? '实时推送已连接，数据将自动刷新'
                : '点击重新连接实时推送'
            }
          >
            <Badge
              status={sseStatusConfig[sseStatus].color}
              text={
                <span
                  className={`text-xs cursor-pointer ${sseStatus !== 'connected' ? 'hover:text-blue-500' : ''}`}
                  onClick={sseStatus !== 'connected' ? sseReconnect : undefined}
                >
                  {sseStatus === 'connected' ? (
                    <Space size={4}>
                      <LinkOutlined />
                      {sseStatusConfig[sseStatus].text}
                    </Space>
                  ) : sseStatus === 'error' || sseStatus === 'disconnected' ? (
                    <Space size={4}>
                      <DisconnectOutlined />
                      {sseStatusConfig[sseStatus].text}
                      <ReloadOutlined />
                    </Space>
                  ) : (
                    sseStatusConfig[sseStatus].text
                  )}
                </span>
              }
            />
          </Tooltip>
        </div>

        <Space>
          <RangePicker
            onChange={handleDateChange}
            placeholder={['开始日期', '结束日期']}
          />
          <Select
            placeholder="筛选职位"
            allowClear
            style={{ width: 140 }}
            onChange={(v) => setQuery((q) => ({ ...q, jobId: v }))}
            options={[]}   // Would populate with job list if needed
          />
        </Space>
      </div>

      <Spin spinning={loading && !overview}>
        {overview ? (
          <motion.div variants={staggerContainer} initial="initial" animate="animate">
            {/* ===== KPI 卡片 ===== */}
            <motion.div variants={staggerItem}>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4 mb-5">
                {kpiCards.map((kpi) => (
                  <Card bordered={false} key={kpi.title} className="overflow-hidden">
                    <div className="flex items-start gap-3">
                      <div
                        className={`w-10 h-10 rounded-xl ${kpi.bg} flex items-center justify-center text-lg shrink-0`}
                        style={{ color: kpi.color }}
                      >
                        {kpi.icon}
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs text-gray-500 mb-0.5 truncate">{kpi.title}</div>
                        <div className="text-xl font-bold text-gray-900 leading-none">
                          {kpi.precision !== undefined
                            ? Number(kpi.value).toFixed(kpi.precision)
                            : kpi.value}
                          {kpi.suffix && (
                            <span className="text-sm font-normal text-gray-400">
                              {kpi.suffix}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            </motion.div>

            {/* ===== 额外 KPI: 匹配分 + 招聘周期 ===== */}
            <motion.div variants={staggerItem}>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-5">
                <Card bordered={false}>
                  <Statistic
                    title="平均 AI 匹配分"
                    value={overview.avgMatchScore ?? '-'}
                    precision={1}
                    suffix="/ 100"
                    valueStyle={{
                      color:
                        (overview.avgMatchScore ?? 0) >= 70
                          ? '#10b981'
                          : (overview.avgMatchScore ?? 0) >= 50
                            ? '#f59e0b'
                            : '#ef4444',
                      fontWeight: 700,
                    }}
                  />
                </Card>
                <Card bordered={false}>
                  <Statistic
                    title="平均招聘周期"
                    value={overview.avgDaysToOffer ?? '-'}
                    precision={1}
                    suffix="天"
                    valueStyle={{ color: '#6366f1', fontWeight: 700 }}
                  />
                </Card>
              </div>
            </motion.div>

            {/* ===== 漏斗 + 转化率 ===== */}
            <motion.div variants={staggerItem}>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-5">
                <Card bordered={false} title="招聘漏斗">
                  {funnelOption ? (
                    <ReactECharts option={funnelOption} style={{ height: 340 }} />
                  ) : (
                    <Empty description="暂无漏斗数据" />
                  )}
                </Card>
                <Card bordered={false} title="阶段转化率">
                  {conversionOption ? (
                    <ReactECharts option={conversionOption} style={{ height: 340 }} />
                  ) : (
                    <Empty description="暂无转化率数据" />
                  )}
                </Card>
              </div>
            </motion.div>

            {/* ===== 月度趋势 ===== */}
            <motion.div variants={staggerItem}>
              <Card bordered={false} title="月度申请趋势">
                {trendOption ? (
                  <ReactECharts option={trendOption} style={{ height: 300 }} />
                ) : (
                  <Empty description="暂无趋势数据" />
                )}
              </Card>
            </motion.div>

            {/* ===== 漏斗详情表格 ===== */}
            <motion.div variants={staggerItem}>
              <Card bordered={false} title="漏斗阶段明细" className="mt-5">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-100">
                        <th className="text-left py-3 px-4 text-gray-500 font-medium">阶段</th>
                        <th className="text-right py-3 px-4 text-gray-500 font-medium">数量</th>
                        <th className="text-right py-3 px-4 text-gray-500 font-medium">占比</th>
                        <th className="text-right py-3 px-4 text-gray-500 font-medium">转化率</th>
                        <th className="text-left py-3 px-4 text-gray-500 font-medium w-1/3">
                          分布
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {overview.funnel.map((stage) => (
                        <tr
                          key={stage.stage}
                          className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors"
                        >
                          <td className="py-3 px-4">
                            <Tag
                              color={stageColors[stage.stage]}
                              className="!text-white !border-0"
                            >
                              {stage.stageLabel}
                            </Tag>
                          </td>
                          <td className="py-3 px-4 text-right font-semibold">{stage.count}</td>
                          <td className="py-3 px-4 text-right text-gray-600">
                            {stage.percentage.toFixed(1)}%
                          </td>
                          <td className="py-3 px-4 text-right">
                            <span
                              className={`font-semibold ${
                                stage.conversionRate >= 50
                                  ? 'text-green-600'
                                  : stage.conversionRate >= 20
                                    ? 'text-amber-600'
                                    : 'text-red-500'
                              }`}
                            >
                              {stage.conversionRate.toFixed(1)}%
                            </span>
                          </td>
                          <td className="py-3 px-4">
                            <div className="w-full bg-gray-100 rounded-full h-2">
                              <div
                                className="h-2 rounded-full transition-all duration-500"
                                style={{
                                  width: `${Math.max(stage.percentage, 2)}%`,
                                  backgroundColor: stageColors[stage.stage] || '#6b7280',
                                }}
                              />
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            </motion.div>
          </motion.div>
        ) : (
          !loading && <Empty description="暂无分析数据" className="mt-20" />
        )}
      </Spin>
    </PageTransition>
  );
}
