import { useState } from 'react';
import {
  Card,
  Input,
  Button,
  Typography,
  Space,
  Tag,
  Spin,
  Empty,
  Row,
  Col,
  Progress,
} from 'antd';
import {
  SearchOutlined,
  UserOutlined,
  TrophyOutlined,
  BookOutlined,
  BulbOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { candidateApi } from '../../api';
import type { SmartSearchResponse, MatchedCandidate } from '../../types';

const { Title, Text } = Typography;
const { TextArea } = Input;

export default function SmartSearchPage() {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<SmartSearchResponse | null>(null);

  const handleSearch = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setResult(null);
    try {
      const { data } = await candidateApi.smartSearch({ query, topK: 10 });
      setResult(data.data || null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Title level={4} style={{ marginBottom: 8 }}>智能候选人搜索</Title>
      <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
        使用自然语言描述您的需求，AI 将基于语义匹配为您推荐最合适的候选人
      </Text>

      {/* 搜索区域 */}
      <Card
        bordered={false}
        style={{
          marginBottom: 24,
          background: 'linear-gradient(135deg, #f0f0ff 0%, #e8f4fd 100%)',
        }}
      >
        <div style={{ maxWidth: 800, margin: '0 auto' }}>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <TextArea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="例如：找一位有 5 年以上 Java 开发经验，熟悉 Spring Boot 和微服务架构，有大型电商项目经历的候选人"
                autoSize={{ minRows: 2, maxRows: 4 }}
                style={{ borderRadius: 12, fontSize: 15 }}
                onPressEnter={(e) => {
                  if (!e.shiftKey) {
                    e.preventDefault();
                    handleSearch();
                  }
                }}
              />
              <Text type="secondary" style={{ fontSize: 12, marginTop: 4, display: 'block' }}>
                提示：描述越详细，匹配结果越精准。支持技能、经验、学历、项目经历等多维度搜索。
              </Text>
            </div>
            <Button
              type="primary"
              size="large"
              icon={<SearchOutlined />}
              loading={loading}
              onClick={handleSearch}
              style={{ borderRadius: 12, height: 56, paddingInline: 32 }}
            >
              搜索
            </Button>
          </div>

          {/* 快捷示例 */}
          <Space size={8} style={{ marginTop: 12 }} wrap>
            <Text type="secondary" style={{ fontSize: 12 }}>试试：</Text>
            {[
              '5年+ Java高级工程师',
              '全栈开发，熟悉React和Node.js',
              '数据分析师，精通Python和SQL',
              '产品经理，有B端SaaS经验',
            ].map((example) => (
              <Tag
                key={example}
                style={{ cursor: 'pointer', borderRadius: 6 }}
                onClick={() => {
                  setQuery(example);
                }}
              >
                {example}
              </Tag>
            ))}
          </Space>
        </div>
      </Card>

      {/* 加载中 */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 60 }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>
            <Text type="secondary">
              <RobotOutlined /> AI 正在为您匹配最佳候选人...
            </Text>
          </div>
        </div>
      )}

      {/* 搜索结果 */}
      {!loading && result && (
        <>
          <div style={{ marginBottom: 16 }}>
            <Text type="secondary">
              共找到 <Text strong>{result.totalMatches || 0}</Text> 位匹配候选人
            </Text>
          </div>

          {/* 候选人卡片 */}
          {result.candidates?.length > 0 ? (
            <Row gutter={[16, 16]}>
              {result.candidates.map((candidate, idx) => (
                <Col span={12} key={candidate.candidateId}>
                  <CandidateCard candidate={candidate} rank={idx + 1} />
                </Col>
              ))}
            </Row>
          ) : (
            <Empty description="未找到匹配候选人，请调整搜索描述" />
          )}
        </>
      )}

      {/* 初始状态 */}
      {!loading && !result && (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <RobotOutlined style={{ fontSize: 64, color: '#c7d2fe' }} />
          <div style={{ marginTop: 16 }}>
            <Text type="secondary" style={{ fontSize: 16 }}>输入描述开始智能搜索</Text>
          </div>
        </div>
      )}
    </div>
  );
}

function CandidateCard({ candidate, rank }: { candidate: MatchedCandidate; rank: number }) {
  const getScoreColor = (score: number) => {
    if (score >= 80) return '#10b981';
    if (score >= 60) return '#f59e0b';
    return '#94a3b8';
  };

  const scorePercent = Math.round(candidate.matchScore);

  return (
    <Card
      bordered={false}
      hoverable
      style={{ height: '100%', border: '1px solid #e2e8f0' }}
      bodyStyle={{ padding: 20 }}
    >
      <div style={{ display: 'flex', gap: 16 }}>
        {/* 排名 */}
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            background: rank <= 3 ? '#4f46e5' : '#e2e8f0',
            color: rank <= 3 ? '#fff' : '#64748b',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            fontSize: 14,
            flexShrink: 0,
          }}
        >
          {rank}
        </div>

        <div style={{ flex: 1 }}>
          {/* 头部 */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <Text strong style={{ fontSize: 16 }}>
                <UserOutlined style={{ marginRight: 6 }} />
                {candidate.name}
              </Text>
              <div style={{ marginTop: 4, color: '#64748b', fontSize: 13 }}>
                {candidate.education && (
                  <span><BookOutlined /> {candidate.education}</span>
                )}
                {candidate.workYears !== undefined && candidate.workYears !== null && (
                  <span style={{ marginLeft: 12 }}>
                    <TrophyOutlined /> {candidate.workYears} 年经验
                  </span>
                )}
              </div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <Progress
                type="circle"
                size={50}
                percent={scorePercent}
                strokeColor={getScoreColor(candidate.matchScore)}
                format={() => `${scorePercent}`}
              />
              <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>匹配度</div>
            </div>
          </div>

          {/* 当前职位 */}
          {(candidate.currentPosition || candidate.currentCompany) && (
            <div style={{ marginTop: 8, color: '#94a3b8', fontSize: 12 }}>
              {candidate.currentPosition}{candidate.currentCompany && ` @ ${candidate.currentCompany}`}
            </div>
          )}

          {/* 技能 */}
          {candidate.skills?.length > 0 && (
            <div style={{ marginTop: 10 }}>
              <Space size={4} wrap>
                {candidate.skills.slice(0, 6).map((s) => (
                  <Tag key={s} color="blue" style={{ borderRadius: 4 }}>{s}</Tag>
                ))}
                {candidate.skills.length > 6 && <Tag>+{candidate.skills.length - 6}</Tag>}
              </Space>
            </div>
          )}

          {/* AI 摘要 */}
          {candidate.aiSummary && (
            <div
              style={{
                marginTop: 10,
                padding: '8px 12px',
                borderRadius: 8,
                background: '#f0f9ff',
                fontSize: 13,
                color: '#475569',
              }}
            >
              <BulbOutlined style={{ color: '#4f46e5', marginRight: 6 }} />
              {candidate.aiSummary}
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}
