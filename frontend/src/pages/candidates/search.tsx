import { useState } from 'react';
import {
  Card,
  Input,
  Button,
  Typography,
  Tag,
  Spin,
  Empty,
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
import { motion } from 'framer-motion';
import { candidateApi } from '../../api';
import type { SmartSearchResponse, MatchedCandidate } from '../../types';
import PageTransition from '../../components/PageTransition';
import { staggerContainer, staggerItem } from '../../components/motionVariants';

const { Text } = Typography;
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
    <PageTransition>
      <h2 className="text-xl font-semibold text-zen-ink mb-1">智能候选人搜索</h2>
      <p className="text-zen-muted mb-6">
        使用自然语言描述您的需求，AI 将基于语义匹配为您推荐最合适的候选人
      </p>

      {/* 搜索区域 */}
      <Card bordered={false} className="mb-6 bg-gradient-to-br from-zen-light to-blue-50/60">
        <div className="max-w-[800px] mx-auto">
          <div className="flex gap-3 items-start">
            <div className="flex-1">
              <TextArea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="例如：找一位有 5 年以上 Java 开发经验，熟悉 Spring Boot 和微服务架构，有大型电商项目经历的候选人"
                autoSize={{ minRows: 2, maxRows: 4 }}
                className="!rounded-xl !text-[15px]"
                onPressEnter={(e) => { if (!e.shiftKey) { e.preventDefault(); handleSearch(); } }}
              />
              <p className="text-zen-muted text-xs mt-1">
                提示：描述越详细，匹配结果越精准。支持技能、经验、学历、项目经历等多维度搜索。
              </p>
            </div>
            <Button
              type="primary"
              size="large"
              icon={<SearchOutlined />}
              loading={loading}
              onClick={handleSearch}
              className="!rounded-xl !h-14 !px-8"
            >
              搜索
            </Button>
          </div>

          {/* 快捷示例 */}
          <div className="flex flex-wrap items-center gap-2 mt-3">
            <span className="text-zen-muted text-xs">试试：</span>
            {[
              '5年+ Java高级工程师',
              '全栈开发，熟悉React和Node.js',
              '数据分析师，精通Python和SQL',
              '产品经理，有B端SaaS经验',
            ].map((example) => (
              <Tag
                key={example}
                className="cursor-pointer !rounded-md hover:!border-accent transition-colors"
                onClick={() => setQuery(example)}
              >
                {example}
              </Tag>
            ))}
          </div>
        </div>
      </Card>

      {/* 加载中 */}
      {loading && (
        <div className="text-center py-16">
          <Spin size="large" />
          <p className="mt-4 text-zen-muted">
            <RobotOutlined className="mr-1" /> AI 正在为您匹配最佳候选人...
          </p>
        </div>
      )}

      {/* 搜索结果 */}
      {!loading && result && (
        <>
          <p className="text-zen-muted mb-4">
            共找到 <span className="font-semibold text-zen-ink">{result.totalMatches || 0}</span> 位匹配候选人
          </p>

          {result.candidates?.length > 0 ? (
            <motion.div
              className="grid grid-cols-2 gap-4"
              variants={staggerContainer}
              initial="initial"
              animate="animate"
            >
              {result.candidates.map((candidate, idx) => (
                <motion.div key={candidate.candidateId} variants={staggerItem}>
                  <CandidateCard candidate={candidate} rank={idx + 1} />
                </motion.div>
              ))}
            </motion.div>
          ) : (
            <Empty description="未找到匹配候选人，请调整搜索描述" />
          )}
        </>
      )}

      {/* 初始状态 */}
      {!loading && !result && (
        <div className="text-center py-16">
          <RobotOutlined className="text-6xl text-zen-border" />
          <p className="mt-4 text-zen-muted text-base">输入描述开始智能搜索</p>
        </div>
      )}
    </PageTransition>
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
    <Card bordered={false} hoverable className="h-full !border !border-zen-border">
      <div className="flex gap-4">
        {/* 排名 */}
        <div
          className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm shrink-0 ${
            rank <= 3 ? 'bg-zen-ink text-white' : 'bg-zen-light text-zen-muted'
          }`}
        >
          {rank}
        </div>

        <div className="flex-1 min-w-0">
          {/* 头部 */}
          <div className="flex justify-between items-start">
            <div>
              <Text strong className="text-base">
                <UserOutlined className="mr-1.5" />{candidate.name}
              </Text>
              <div className="mt-1 text-zen-muted text-sm">
                {candidate.education && <span><BookOutlined className="mr-1" />{candidate.education}</span>}
                {candidate.workYears !== undefined && candidate.workYears !== null && (
                  <span className="ml-3"><TrophyOutlined className="mr-1" />{candidate.workYears} 年经验</span>
                )}
              </div>
            </div>
            <div className="text-center">
              <Progress
                type="circle"
                size={50}
                percent={scorePercent}
                strokeColor={getScoreColor(candidate.matchScore)}
                format={() => `${scorePercent}`}
              />
              <div className="text-[11px] text-zen-muted mt-0.5">匹配度</div>
            </div>
          </div>

          {/* 当前职位 */}
          {(candidate.currentPosition || candidate.currentCompany) && (
            <p className="mt-2 text-zen-muted text-xs">
              {candidate.currentPosition}{candidate.currentCompany && ` @ ${candidate.currentCompany}`}
            </p>
          )}

          {/* 技能 */}
          {candidate.skills?.length > 0 && (
            <div className="mt-2.5 flex flex-wrap gap-1">
              {candidate.skills.slice(0, 6).map((s) => (
                <Tag key={s} color="blue" className="!rounded">{s}</Tag>
              ))}
              {candidate.skills.length > 6 && <Tag>+{candidate.skills.length - 6}</Tag>}
            </div>
          )}

          {/* AI 摘要 */}
          {candidate.aiSummary && (
            <div className="mt-2.5 p-2 px-3 rounded-lg bg-accent-light/30 text-sm text-zen-secondary">
              <BulbOutlined className="text-accent mr-1.5" />
              {candidate.aiSummary}
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}
