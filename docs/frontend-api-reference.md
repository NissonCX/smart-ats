# SmartATS 前端 API 完整参考手册

> 自动生成于 2026-02-24，基于后端源码全量分析

---

## 目录
- [全局约定](#全局约定)
- [统一响应结构 Result\<T\>](#统一响应结构-resultt)
- [错误码 ResultCode](#错误码-resultcode)
- [认证与权限](#认证与权限)
- [模块一：Auth 认证管理](#模块一auth-认证管理)
- [模块二：Job 职位管理](#模块二job-职位管理)
- [模块三：Resume 简历管理](#模块三resume-简历管理)
- [模块四：Candidate 候选人管理](#模块四candidate-候选人管理)
- [模块五：Smart Search 智能搜索](#模块五smart-search-智能搜索)
- [模块六：Application 职位申请管理](#模块六application-职位申请管理)
- [模块七：Interview 面试管理](#模块七interview-面试管理)
- [模块八：Webhook 管理](#模块八webhook-管理)
- [分页结构 Page\<T\>](#分页结构-paget)
- [Entity 实体完整字段](#entity-实体完整字段)
- [TypeScript 类型定义](#typescript-类型定义)

---

## 全局约定

| 配置项 | 值 |
|--------|-----|
| Base URL | `http://localhost:8080/api/v1` |
| Server Port | `8080` |
| Context Path | `/api/v1` |
| Content-Type | `application/json` (除文件上传用 `multipart/form-data`) |
| 认证方式 | `Authorization: Bearer <accessToken>` |
| 日期格式 (JSON) | `yyyy-MM-dd HH:mm:ss` |
| 时区 | `Asia/Shanghai` |
| null 值 | 不序列化（Jackson `non_null`） |
| 文件大小限制 | 单文件 10MB，请求 50MB |
| CORS | 开发环境 `*`，生产读 `CORS_ALLOWED_ORIGINS` 环境变量 |

---

## 统一响应结构 Result\<T\>

**所有 API 返回统一包装：**

```typescript
interface Result<T> {
  code: number;       // 200=成功，其他=错误码
  message: string;    // "success" 或错误描述
  data: T | null;     // 响应数据，失败时为 null
  timestamp: number;  // 毫秒级时间戳
}
```

---

## 错误码 ResultCode

| 枚举名 | Code | Message | 场景 |
|---------|------|---------|------|
| `SUCCESS` | 200 | 操作成功 | — |
| **通用错误** | | | |
| `BAD_REQUEST` | 40001 | 参数校验失败 | 请求参数不合法 |
| `UNAUTHORIZED` | 40101 | 未登录，请先登录 | Token 缺失/无效 |
| `FORBIDDEN` | 40301 | 无权限访问 | 角色权限不足 |
| `NOT_FOUND` | 40401 | 资源不存在 | 通用 404 |
| **系统错误** | | | |
| `INTERNAL_ERROR` | 50001 | 系统内部错误 | 未预期异常 |
| `AI_SERVICE_ERROR` | 50002 | AI服务不可用 | 智谱AI调用失败 |
| `FILE_UPLOAD_ERROR` | 50003 | 文件上传失败 | MinIO上传异常 |
| **认证模块 10xxx** | | | |
| `USER_NOT_FOUND` | 10001 | 用户不存在 | |
| `USER_ALREADY_EXISTS` | 10002 | 用户已存在 | |
| `USERNAME_ALREADY_EXISTS` | 10003 | 用户名已存在 | |
| `EMAIL_ALREADY_EXISTS` | 10004 | 邮箱已被注册 | |
| `PASSWORD_ERROR` | 10005 | 密码错误 | |
| `INVALID_CREDENTIALS` | 10006 | 用户名或密码错误 | |
| `USER_DISABLED` | 10007 | 账号已禁用 | |
| `ACCOUNT_DISABLED` | 10008 | 账号已被禁用，请联系管理员 | |
| `TOKEN_INVALID` | 10009 | Token 无效或已过期 | |
| `TOKEN_REFRESH_FAILED` | 10010 | Token 刷新失败 | |
| **验证码 11xxx** | | | |
| `VERIFICATION_CODE_SEND_TOO_FREQUENT` | 11001 | 验证码发送过于频繁，请稍后再试 | 60秒限流 |
| `VERIFICATION_CODE_SEND_FAILED` | 11002 | 验证码发送失败，请稍后重试 | |
| `VERIFICATION_CODE_INVALID` | 11003 | 验证码错误或已过期 | 5分钟有效期 |
| `VERIFICATION_CODE_REQUIRED` | 11004 | 请先获取验证码 | |
| **简历模块 20xxx** | | | |
| `RESUME_NOT_FOUND` | 20001 | 简历不存在 | |
| `RESUME_ALREADY_PARSED` | 20002 | 简历已解析，请勿重复提交 | |
| `FILE_TYPE_NOT_SUPPORTED` | 20003 | 不支持的文件类型 | 仅 PDF/DOC/DOCX |
| `FILE_SIZE_EXCEEDED` | 20004 | 文件大小超限 | >10MB |
| `RESUME_DUPLICATE` | 20005 | 简历文件已存在 | MD5去重 |
| `BATCH_UPLOAD_LIMIT_EXCEEDED` | 20006 | 批量上传文件数超限（最多20个） | |
| `UPLOAD_RATE_LIMITED` | 20007 | 上传过于频繁，请稍后再试 | 5次/分钟 |
| **AI 30xxx** | | | |
| `AI_QUOTA_EXCEEDED` | 30001 | AI调用次数超限 | |
| `AI_PARSE_FAILED` | 30002 | 简历解析失败 | |
| **职位申请 42xxx** | | | |
| `APPLICATION_NOT_FOUND` | 42001 | 申请记录不存在 | |
| `APPLICATION_DUPLICATE` | 42002 | 该候选人已申请此职位 | |
| `APPLICATION_STATUS_INVALID` | 42003 | 申请状态流转不合法 | |
| `APPLICATION_JOB_NOT_PUBLISHED` | 42004 | 职位未发布，无法申请 | |
| **面试 43xxx** | | | |
| `INTERVIEW_NOT_FOUND` | 43001 | 面试记录不存在 | |
| `INTERVIEW_ALREADY_COMPLETED` | 43002 | 面试已完成，无法修改 | |
| `INTERVIEW_ALREADY_CANCELLED` | 43003 | 面试已取消 | |
| `INTERVIEW_TIME_CONFLICT` | 43004 | 面试时间冲突 | |

---

## 认证与权限

**SecurityConfig 公开端点（无需 Token）：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 注册 |
| POST | `/auth/login` | 登录 |
| POST | `/auth/send-verification-code` | 发验证码 |
| POST | `/auth/refresh` | 刷新Token |
| GET | `/jobs/**` | 所有职位GET接口（匿名浏览） |
| — | `/swagger-ui/**`, `/v3/api-docs/**` | API文档 |

**所有其他端点**均需要在请求头携带 `Authorization: Bearer <accessToken>`。

**用户角色**（3种）：`ADMIN` | `HR` | `INTERVIEWER`
- 注册时只能选 `HR` 或 `INTERVIEWER`，`ADMIN` 禁止自注册

---

## 模块一：Auth 认证管理

**Base Path**: `/auth`

### 1.1 用户注册
```
POST /api/v1/auth/register
Auth: ❌
```

**Request Body:**
```typescript
interface RegisterRequest {
  username: string;          // @NotBlank, @Size(min=4, max=20)
  password: string;          // @NotBlank, @Size(min=6, max=20)
  email: string;             // @NotBlank, @Email
  role?: string;             // @Pattern("HR|INTERVIEWER"), 可选, 默认 HR
  verificationCode: string;  // @NotBlank, @Pattern("^\\d{6}$"), 6位数字
}
```

**Response:** `Result<null>`

---

### 1.2 用户登录
```
POST /api/v1/auth/login
Auth: ❌
```

**Request Body:**
```typescript
interface LoginRequest {
  username: string;  // @NotBlank
  password: string;  // @NotBlank
}
```

**Response:** `Result<LoginResponse>`
```typescript
interface LoginResponse {
  accessToken: string;    // JWT, 2小时有效
  refreshToken: string;   // 7天有效
  expiresIn: number;      // 过期时间(秒)
  userInfo: UserInfo;
}

interface UserInfo {
  userId: number;          // Long
  username: string;
  email: string;
  role: string;            // "ADMIN" | "HR" | "INTERVIEWER"
  dailyAiQuota: number;   // 每日AI配额
  todayAiUsed: number;    // 今日已用AI次数
}
```

---

### 1.3 发送邮箱验证码
```
POST /api/v1/auth/send-verification-code
Auth: ❌
```

**Request Body:**
```typescript
interface SendVerificationCodeRequest {
  email: string;  // @NotBlank, @Email
}
```

**Response:** `Result<null>`

> ⚠️ 60秒发送限流，验证码5分钟有效

---

### 1.4 刷新 Token
```
POST /api/v1/auth/refresh
Auth: ❌
```

**Request Body:**
```typescript
interface RefreshTokenRequest {
  refreshToken: string;  // @NotBlank
}
```

**Response:** `Result<LoginResponse>` （结构同登录响应）

---

### 1.5 测试 JWT 认证
```
GET /api/v1/auth/test
Auth: ✅
```

**Response:** `Result<TestAuthResponse>`
```typescript
interface TestAuthResponse {
  userId: number;
  authorities: any;
  message: string;    // "JWT 认证成功！"
}
```

---

## 模块二：Job 职位管理

**Base Path**: `/jobs`

### 2.1 创建职位
```
POST /api/v1/jobs
Auth: ✅
```

**Request Body:**
```typescript
interface CreateJobRequest {
  title: string;              // @NotBlank
  department?: string;
  description: string;        // @NotBlank
  requirements: string;       // @NotBlank
  requiredSkills?: string[];  // 技能标签数组
  salaryMin: number;          // @NotNull, @Min(0), 单位: K
  salaryMax: number;          // @NotNull, @Min(0), 单位: K
  experienceMin?: number;     // @Min(0), 年
  experienceMax?: number;     // @Min(0), 年
  education?: string;         // 如: "本科", "硕士"
  jobType?: string;           // 如: "全职", "兼职", "实习"
}
```

**Response:** `Result<number>` — 返回创建的职位 ID (Long)

---

### 2.2 更新职位
```
PUT /api/v1/jobs
Auth: ✅
```

**Request Body:**
```typescript
interface UpdateJobRequest {
  id: number;                 // @NotNull, 职位ID
  title?: string;
  department?: string;
  description?: string;
  requirements?: string;
  requiredSkills?: string[];
  salaryMin?: number;         // @Min(0)
  salaryMax?: number;         // @Min(0)
  experienceMin?: number;     // @Min(0)
  experienceMax?: number;     // @Min(0)
  education?: string;
  jobType?: string;
}
```

**Response:** `Result<null>`

---

### 2.3 获取职位详情
```
GET /api/v1/jobs/{id}
Auth: ❌ (公开)
Path Params: id (Long)
```

**Response:** `Result<JobResponse>`
```typescript
interface JobResponse {
  id: number;
  title: string;
  department: string;
  description: string;
  requirements: string;
  requiredSkills: string[];
  salaryMin: number;           // K
  salaryMax: number;           // K
  salaryRange: string;         // 格式化, 如 "15K-25K"
  experienceMin: number;
  experienceMax: number;
  experienceRange: string;     // 格式化, 如 "3-5年"
  education: string;
  jobType: string;
  status: string;              // "DRAFT" | "PUBLISHED" | "CLOSED"
  statusDesc: string;          // 中文状态描述
  creatorId: number;
  viewCount: number;
  createdAt: string;           // "yyyy-MM-dd HH:mm:ss"
  updatedAt: string;           // "yyyy-MM-dd HH:mm:ss"
}
```

---

### 2.4 职位列表 (分页)
```
GET /api/v1/jobs
Auth: ❌ (公开)
```

**Query Params:**
```typescript
interface JobQueryRequest {
  keyword?: string;          // 关键词搜索 (标题/描述/要求)
  department?: string;
  jobType?: string;
  education?: string;
  experienceMin?: number;
  salaryMin?: number;
  status?: string;           // "DRAFT" | "PUBLISHED" | "CLOSED"
  pageNum?: number;          // 默认 1
  pageSize?: number;         // 默认 10
  orderBy?: string;          // "created_at" (默认) | "salary_max" | "view_count"
  orderDirection?: string;   // "asc" | "desc" (默认)
}
```

**Response:** `Result<Page<JobResponse>>`

---

### 2.5 发布职位
```
POST /api/v1/jobs/{id}/publish
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 2.6 关闭职位
```
POST /api/v1/jobs/{id}/close
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 2.7 删除职位
```
DELETE /api/v1/jobs/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 2.8 热门职位
```
GET /api/v1/jobs/hot
Auth: ❌ (公开)
```

**Query Params:**
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `limit` | number | 10 | 返回数量 |

**Response:** `Result<JobResponse[]>`

---

## 模块三：Resume 简历管理

**Base Path**: `/resumes`

### 3.1 上传简历
```
POST /api/v1/resumes/upload
Auth: ✅
Content-Type: multipart/form-data
```

**Request Body (FormData):**
| 字段 | 类型 | 说明 |
|------|------|------|
| `file` | File | PDF/DOC/DOCX, ≤10MB |

**Response:** `Result<ResumeUploadResponse>`
```typescript
interface ResumeUploadResponse {
  taskId: string;        // 用于轮询解析状态
  resumeId: number;
  duplicated: boolean;   // 是否已存在(MD5去重)
  message: string;
}
```

---

### 3.2 批量上传简历
```
POST /api/v1/resumes/batch-upload
Auth: ✅
Content-Type: multipart/form-data
```

**Request Body (FormData):**
| 字段 | 类型 | 说明 |
|------|------|------|
| `files` | File[] | 最多20个, 每分钟限5次 |

**Response:** `Result<BatchUploadResponse>`
```typescript
interface BatchUploadResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  items: BatchUploadItem[];
}

interface BatchUploadItem {
  taskId: string | null;  // 成功时有值
  resumeId: number | null;
  fileName: string;
  status: string;          // "QUEUED" | "DUPLICATE" | "FAILED"
  message: string;
}
```

---

### 3.3 查询解析任务状态
```
GET /api/v1/resumes/tasks/{taskId}
Auth: ✅
Path Params: taskId (string, UUID)
```

**Response:** `Result<TaskStatusResponse>`
```typescript
interface TaskStatusResponse {
  status: string;           // "PENDING" | "PARSING" | "COMPLETED" | "FAILED"
  resumeId: number | null;  // 解析完成后有值
  candidateId: number | null; // 解析完成后有值
  errorMessage: string | null;
  progress: number | null;   // 0-100
}
```

> ⚠️ 前端应轮询此接口直到 status 为 COMPLETED 或 FAILED

---

### 3.4 获取简历详情
```
GET /api/v1/resumes/{id}
Auth: ✅ (仅能查看当前用户的简历)
Path Params: id (Long)
```

**Response:** `Result<Resume>`
```typescript
interface Resume {
  id: number;
  userId: number;
  fileName: string;
  filePath: string;
  fileUrl: string;
  fileSize: number;        // bytes
  fileHash: string;        // MD5
  fileType: string;        // "pdf" | "doc" | "docx"
  status: string;          // "PARSING" | "COMPLETED" | "FAILED"
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 3.5 简历列表 (分页)
```
GET /api/v1/resumes
Auth: ✅ (当前用户的简历)
```

**Query Params:**
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | number | 1 | 页码 |
| `size` | number | 10 | 每页条数 |

**Response:** `Result<Page<Resume>>`

---

## 模块四：Candidate 候选人管理

**Base Path**: `/candidates`

### 4.1 根据简历ID查询候选人
```
GET /api/v1/candidates/resume/{resumeId}
Auth: ✅
Path Params: resumeId (Long)
```

**Response:** `Result<CandidateResponse>`

---

### 4.2 查询候选人详情
```
GET /api/v1/candidates/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<CandidateResponse>`
```typescript
interface CandidateResponse {
  id: number;
  resumeId: number;

  // 基本信息
  name: string;
  phone: string;          // 脱敏: "138****5678"
  email: string;          // 脱敏: "zh****@example.com"
  gender: string;
  age: number;

  // 教育信息
  education: string;      // 最高学历
  school: string;         // 毕业院校
  major: string;
  graduationYear: number;

  // 工作信息
  workYears: number;
  currentCompany: string;
  currentPosition: string;

  // 技能与经历
  skills: string[];
  workExperience: WorkExperienceItem[];
  projectExperience: ProjectExperienceItem[];
  selfEvaluation: string;

  // AI 元数据
  confidenceScore: number;   // 0-1
  aiSummary: string;

  // 时间
  parsedAt: string;
  createdAt: string;
  updatedAt: string;
}

// workExperience 数组中的单项结构 (Map<String, Object>):
interface WorkExperienceItem {
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  description: string;
}

// projectExperience 数组中的单项结构 (Map<String, Object>):
interface ProjectExperienceItem {
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  technologies: string[];
}
```

> ⚠️ `phone` 和 `email` 始终经过数据脱敏，前端不会拿到原始值

---

### 4.3 更新候选人信息
```
PUT /api/v1/candidates/{id}
Auth: ✅
Path Params: id (Long)
```

**Request Body:**
```typescript
interface CandidateUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  workYears?: number;
  currentCompany?: string;
  currentPosition?: string;
  skills?: string[];
  selfEvaluation?: string;
}
```

> 仅更新非 null 字段，异步重新向量化

**Response:** `Result<CandidateResponse>`

---

### 4.4 删除候选人
```
DELETE /api/v1/candidates/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

> 同步删除 Milvus 向量 + MySQL 记录

---

### 4.5 候选人列表 (分页 + 多维筛选)
```
GET /api/v1/candidates
Auth: ✅
```

**Query Params:**
```typescript
interface CandidateQueryParams {
  page?: number;             // 默认 1
  pageSize?: number;         // 默认 10, 最大 100
  keyword?: string;          // 模糊匹配: 姓名/邮箱/公司/职位
  education?: string;        // 精确匹配: "本科"/"硕士"/"博士"
  skill?: string;            // 精确匹配JSON数组中的技能, 如: "Java"
  minWorkYears?: number;     // 工作年限下限(含)
  maxWorkYears?: number;     // 工作年限上限(含)
  currentPosition?: string;  // 模糊匹配
}
```

**Response:** `Result<Page<CandidateResponse>>`

---

## 模块五：Smart Search 智能搜索

**Base Path**: `/candidates`

### 5.1 语义搜索候选人
```
POST /api/v1/candidates/smart-search
Auth: ✅
```

**Request Body:**
```typescript
interface SmartSearchRequest {
  query: string;          // @NotBlank, @Size(max=1000), 自然语言查询
  topK?: number;          // @Min(1), @Max(50), 默认 10
  minScore?: number;      // @Min(0), @Max(1), 默认 0.3, COSINE相似度阈值
}
```

> 查询示例:
> - "3年以上 Java 后端开发，熟悉 Spring Boot 和微服务架构"
> - "有大厂经验的前端工程师，精通 React 和 TypeScript"

**Response:** `Result<SmartSearchResponse>`
```typescript
interface SmartSearchResponse {
  query: string;
  totalMatches: number;
  candidates: MatchedCandidate[];
}

interface MatchedCandidate {
  candidateId: number;
  name: string;
  matchScore: number;       // 0~1, COSINE相似度
  currentPosition: string;
  currentCompany: string;
  education: string;
  workYears: number;
  skills: string[];
  aiSummary: string;
}
```

---

## 模块六：Application 职位申请管理

**Base Path**: `/applications`

### 状态流转图
```
PENDING → SCREENING → INTERVIEW → OFFER
                                 → REJECTED
                   → REJECTED
         → WITHDRAWN (任意阶段可撤回)
```

### 6.1 创建职位申请
```
POST /api/v1/applications
Auth: ✅
```

**Request Body:**
```typescript
interface CreateApplicationRequest {
  jobId: number;         // @NotNull
  candidateId: number;   // @NotNull
  hrNotes?: string;
}
```

> 自动去重：同一候选人不能重复申请同一职位

**Response:** `Result<number>` — 返回申请 ID (Long)

---

### 6.2 更新申请状态
```
PUT /api/v1/applications/{id}/status
Auth: ✅
Path Params: id (Long)
```

**Request Body:**
```typescript
interface UpdateApplicationStatusRequest {
  status: string;   // @NotBlank, @Pattern("SCREENING|INTERVIEW|OFFER|REJECTED|WITHDRAWN")
  hrNotes?: string; // HR备注(状态变更原因)
}
```

**Response:** `Result<null>`

---

### 6.3 获取申请详情
```
GET /api/v1/applications/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<ApplicationResponse>`
```typescript
interface ApplicationResponse {
  id: number;

  // 关联信息
  jobId: number;
  jobTitle: string;          // 冗余, 方便展示
  candidateId: number;
  candidateName: string;     // 冗余, 方便展示

  // AI 匹配
  matchScore: number;        // BigDecimal → number, 0-100
  matchReasons: string[];
  matchCalculatedAt: string; // "yyyy-MM-dd HH:mm:ss"

  // 状态
  status: string;            // "PENDING"|"SCREENING"|"INTERVIEW"|"OFFER"|"REJECTED"|"WITHDRAWN"
  statusDesc: string;        // 中文描述
  hrNotes: string;

  // 时间
  appliedAt: string;
  updatedAt: string;
}
```

---

### 6.4 按职位查询申请列表
```
GET /api/v1/applications/job/{jobId}
Auth: ✅
Path Params: jobId (Long)
```

**Query Params:**
| 参数 | 类型 | 默认值 |
|------|------|--------|
| `pageNum` | number | 1 |
| `pageSize` | number | 10 |

**Response:** `Result<Page<ApplicationResponse>>`

---

### 6.5 按候选人查询申请列表
```
GET /api/v1/applications/candidate/{candidateId}
Auth: ✅
Path Params: candidateId (Long)
```

**Query Params:**
| 参数 | 类型 | 默认值 |
|------|------|--------|
| `pageNum` | number | 1 |
| `pageSize` | number | 10 |

**Response:** `Result<Page<ApplicationResponse>>`

---

### 6.6 综合查询申请列表
```
GET /api/v1/applications
Auth: ✅
```

**Query Params:**
```typescript
interface ApplicationQueryRequest {
  pageNum?: number;        // 默认 1
  pageSize?: number;       // 默认 10, 最大 100
  jobId?: number;
  candidateId?: number;
  status?: string;         // "PENDING"|"SCREENING"|"INTERVIEW"|"OFFER"|"REJECTED"|"WITHDRAWN"
  orderBy?: string;        // "applied_at"(默认) | "match_score" | "updated_at"
  orderDirection?: string; // "asc" | "desc"(默认)
}
```

**Response:** `Result<Page<ApplicationResponse>>`

---

## 模块七：Interview 面试管理

**Base Path**: `/interviews`

### 7.1 安排面试
```
POST /api/v1/interviews
Auth: ✅
```

**Request Body:**
```typescript
interface ScheduleInterviewRequest {
  applicationId: number;   // @NotNull
  interviewerId: number;   // @NotNull, 面试官用户ID
  round?: number;          // @Min(1), 面试轮次, 默认自动递增
  interviewType: string;   // @NotNull, @Pattern("PHONE|VIDEO|ONSITE|WRITTEN_TEST")
  scheduledAt: string;     // @NotNull, @Future, ISO datetime "yyyy-MM-dd HH:mm:ss"
  durationMinutes?: number; // @Min(15), 默认 60
}
```

> 面试类型枚举: `PHONE`(电话) | `VIDEO`(视频) | `ONSITE`(现场) | `WRITTEN_TEST`(笔试)

**Response:** `Result<number>` — 返回面试记录 ID (Long)

---

### 7.2 提交面试反馈
```
PUT /api/v1/interviews/{id}/feedback
Auth: ✅
Path Params: id (Long)
```

**Request Body:**
```typescript
interface SubmitFeedbackRequest {
  score: number;            // @Min(1), @Max(10)
  feedback: string;         // @NotBlank
  recommendation: string;   // @NotBlank, @Pattern("STRONG_YES|YES|NEUTRAL|NO|STRONG_NO")
}
```

> 推荐结论枚举: `STRONG_YES` | `YES` | `NEUTRAL` | `NO` | `STRONG_NO`

**Response:** `Result<null>`

---

### 7.3 取消面试
```
POST /api/v1/interviews/{id}/cancel
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 7.4 获取面试记录详情
```
GET /api/v1/interviews/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<InterviewResponse>`
```typescript
interface InterviewResponse {
  id: number;

  // 关联信息
  applicationId: number;
  interviewerId: number;
  interviewerName: string;  // 关联查询的面试官姓名

  // 面试安排
  round: number;
  interviewType: string;         // "PHONE"|"VIDEO"|"ONSITE"|"WRITTEN_TEST"
  interviewTypeDesc: string;     // 中文描述
  scheduledAt: string;           // "yyyy-MM-dd HH:mm:ss"
  durationMinutes: number;

  // 状态与结果
  status: string;                // "SCHEDULED"|"COMPLETED"|"CANCELLED"|"NO_SHOW"
  statusDesc: string;            // 中文描述
  feedback: string | null;
  score: number | null;          // 1-10
  recommendation: string | null; // "STRONG_YES"|"YES"|"NEUTRAL"|"NO"|"STRONG_NO"
  recommendationDesc: string | null; // 中文描述

  // 时间
  createdAt: string;
  updatedAt: string;
}
```

---

### 7.5 按申请查询所有面试轮次
```
GET /api/v1/interviews/application/{applicationId}
Auth: ✅
Path Params: applicationId (Long)
```

**Response:** `Result<InterviewResponse[]>`

---

## 模块八：Webhook 管理

**Base Path**: `/webhooks`

### 支持的事件类型 (12种)
| 事件类型 | 说明 |
|----------|------|
| `resume.uploaded` | 简历已上传 |
| `resume.parse_completed` | 简历解析完成 |
| `resume.parse_failed` | 简历解析失败 |
| `candidate.created` | 候选人已创建 |
| `candidate.updated` | 候选人已更新 |
| `application.submitted` | 申请已提交 |
| `application.status_changed` | 申请状态变更 |
| `interview.scheduled` | 面试已安排 |
| `interview.completed` | 面试已完成 |
| `interview.cancelled` | 面试已取消 |
| `system.error` | 系统错误 |
| `system.maintenance` | 系统维护 |

### 8.1 创建 Webhook 配置
```
POST /api/v1/webhooks
Auth: ✅
```

**Request Body:**
```typescript
interface WebhookCreateRequest {
  url: string;         // @NotBlank, @Pattern(HTTP/HTTPS URL)
  events: string[];    // @NotNull, 事件类型数组
  description?: string;
}
```

**Response:** `Result<WebhookResponse>`
```typescript
interface WebhookResponse {
  id: number;
  url: string;
  events: string[];
  description: string;
  enabled: boolean;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  createdAt: string;
  updatedAt: string;
  secretHint: string;  // 密钥提示, 如 "abc1****xyz9"
}
```

---

### 8.2 获取用户的所有 Webhook 配置
```
GET /api/v1/webhooks
Auth: ✅
```

**Response:** `Result<WebhookResponse[]>`

---

### 8.3 删除 Webhook 配置
```
DELETE /api/v1/webhooks/{id}
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<null>`

---

### 8.4 测试 Webhook
```
POST /api/v1/webhooks/{id}/test
Auth: ✅
Path Params: id (Long)
```

**Response:** `Result<boolean>` — true=发送成功

---

### Webhook 事件推送格式 (发送到用户URL的Payload)
```typescript
interface WebhookPayload {
  eventId: string;                    // UUID
  eventType: string;                  // 事件类型
  timestamp: string;                  // ISO datetime
  data: Record<string, any>;          // 事件数据（按事件类型不同）
  signature: string;                  // HMAC-SHA256 签名
  version: string;                    // "1.0"
}
```

---

## 分页结构 Page\<T\>

后端使用 MyBatis-Plus 的 `Page<T>`，JSON 序列化后结构：

```typescript
interface Page<T> {
  records: T[];        // 当前页数据
  total: number;       // 总记录数
  size: number;        // 每页大小
  current: number;     // 当前页码 (从1开始)
  pages: number;       // 总页数
  // MyBatis-Plus 还会包含以下字段（可忽略）:
  orders?: any[];
  optimizeCountSql?: boolean;
  searchCount?: boolean;
  optimizeJoinOfCountSql?: boolean;
  maxLimit?: number;
  countId?: string;
}
```

---

## Entity 实体完整字段

### User (users 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `username` | String | 用户名（唯一） |
| `password` | String | BCrypt加密（不会返回前端） |
| `email` | String | 邮箱（唯一） |
| `role` | String | "ADMIN" / "HR" / "INTERVIEWER" |
| `dailyAiQuota` | Integer | 每日AI配额 |
| `status` | Integer | 0=禁用, 1=启用 |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### Job (jobs 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `title` | String | 职位标题 |
| `department` | String | 部门 |
| `description` | String | 职位描述 |
| `requirements` | String | 任职要求 |
| `requiredSkills` | String | JSON格式技能标签 |
| `salaryMin` | Integer | 薪资下限(K) |
| `salaryMax` | Integer | 薪资上限(K) |
| `experienceMin` | Integer | 最低经验年限 |
| `experienceMax` | Integer | 最高经验年限 |
| `education` | String | 学历要求 |
| `jobType` | String | 职位类型 |
| `status` | String | "DRAFT" / "PUBLISHED" / "CLOSED" |
| `creatorId` | Long | 创建者ID |
| `viewCount` | Integer | 浏览次数 |
| `deleted` | Integer | 逻辑删除: 0=正常, 1=已删 |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### Resume (resumes 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `userId` | Long | 上传者ID |
| `fileName` | String | 文件名 |
| `filePath` | String | MinIO路径 |
| `fileUrl` | String | 下载URL |
| `fileSize` | Long | 文件大小(bytes) |
| `fileHash` | String | MD5哈希（唯一索引去重） |
| `fileType` | String | 文件类型 |
| `status` | String | "PARSING" / "COMPLETED" / "FAILED" |
| `errorMessage` | String | 错误信息 |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### Candidate (candidates 表)
| 字段 | Java 类型 | DB 列名 | 说明 |
|------|----------|---------|------|
| `id` | Long | id | 主键自增 |
| `resumeId` | Long | resume_id | 简历ID(1:1) |
| `name` | String | name | 姓名 |
| `phone` | String | phone | 手机号 |
| `email` | String | email | 邮箱 |
| `gender` | String | gender | 性别 |
| `age` | Integer | birth_year | 年龄 |
| `education` | String | highest_education | 最高学历 |
| `school` | String | graduate_school | 毕业院校 |
| `major` | String | major | 专业 |
| `graduationYear` | Integer | graduation_year | 毕业年份 |
| `workYears` | Integer | experience_years | 工作年限 |
| `currentCompany` | String | current_company | 当前公司 |
| `currentPosition` | String | current_position | 当前职位 |
| `skills` | List\<String\> | skills | JSON数组 |
| `workExperience` | List\<Map\> | work_experiences | JSON数组 |
| `projectExperience` | List\<Map\> | project_experience | JSON数组 |
| `selfEvaluation` | String | self_evaluation | 自我评价 |
| `rawJson` | String | raw_extracted_json | AI原始JSON |
| `confidenceScore` | Double | confidence_score | 置信度(0-1) |
| `parsedAt` | LocalDateTime | parsed_at | 解析时间 |
| `vectorId` | String | vector_id | Milvus向量ID |
| `aiSummary` | String | ai_summary | AI生成摘要 |
| `createdAt` | LocalDateTime | created_at | |
| `updatedAt` | LocalDateTime | updated_at | |

### JobApplication (job_applications 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `jobId` | Long | 职位ID |
| `candidateId` | Long | 候选人ID |
| `matchScore` | BigDecimal | AI匹配分(0-100) |
| `matchReasons` | String | JSON格式匹配原因 |
| `matchCalculatedAt` | LocalDateTime | 匹配计算时间 |
| `status` | String | "PENDING"/"SCREENING"/"INTERVIEW"/"OFFER"/"REJECTED"/"WITHDRAWN" |
| `hrNotes` | String | HR备注 |
| `appliedAt` | LocalDateTime | 申请时间 |
| `updatedAt` | LocalDateTime | |

### InterviewRecord (interview_records 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `applicationId` | Long | 关联申请ID |
| `interviewerId` | Long | 面试官用户ID |
| `round` | Integer | 面试轮次(1=一面...) |
| `interviewType` | String | "PHONE"/"VIDEO"/"ONSITE"/"WRITTEN_TEST" |
| `scheduledAt` | LocalDateTime | 计划面试时间 |
| `durationMinutes` | Integer | 时长(分钟) |
| `status` | String | "SCHEDULED"/"COMPLETED"/"CANCELLED"/"NO_SHOW" |
| `feedback` | String | 面试反馈 |
| `score` | Integer | 评分(1-10) |
| `recommendation` | String | "STRONG_YES"/"YES"/"NEUTRAL"/"NO"/"STRONG_NO" |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### WebhookConfig (webhook_configs 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `userId` | Long | 用户ID |
| `url` | String | 回调URL |
| `events` | String | JSON数组(订阅事件) |
| `secret` | String | HMAC密钥 |
| `enabled` | Boolean | 是否启用 |
| `description` | String | 描述 |
| `failureCount` | Integer | 连续失败次数 |
| `lastFailureAt` | LocalDateTime | |
| `lastSuccessAt` | LocalDateTime | |
| `createdAt` | LocalDateTime | |
| `updatedAt` | LocalDateTime | |

### WebhookLog (webhook_logs 表)
| 字段 | Java 类型 | 说明 |
|------|----------|------|
| `id` | Long | 主键自增 |
| `webhookId` | Long | Webhook配置ID |
| `eventType` | String | 事件类型 |
| `payload` | String | 请求负载JSON |
| `responseStatus` | Integer | HTTP响应码 |
| `responseBody` | String | 响应内容 |
| `errorMessage` | String | 错误信息 |
| `status` | String | "SUCCESS"/"FAILED"/"RETRYING" |
| `retryCount` | Integer | 重试次数 |
| `duration` | Long | 耗时(ms) |
| `createdAt` | LocalDateTime | |

---

## TypeScript 类型定义

以下是完整的前端 TypeScript 类型定义，可直接复制使用：

```typescript
// ==================== 通用 ====================

/** 统一响应包装 */
export interface Result<T = any> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

/** MyBatis-Plus 分页结构 */
export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ==================== 枚举值 ====================

/** 用户角色 */
export type UserRole = 'ADMIN' | 'HR' | 'INTERVIEWER';

/** 职位状态 */
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';

/** 简历解析状态 */
export type ResumeStatus = 'PARSING' | 'COMPLETED' | 'FAILED';

/** 任务状态 */
export type TaskStatus = 'PENDING' | 'PARSING' | 'COMPLETED' | 'FAILED';

/** 申请状态 */
export type ApplicationStatus = 'PENDING' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';

/** 面试类型 */
export type InterviewType = 'PHONE' | 'VIDEO' | 'ONSITE' | 'WRITTEN_TEST';

/** 面试状态 */
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

/** 面试推荐 */
export type Recommendation = 'STRONG_YES' | 'YES' | 'NEUTRAL' | 'NO' | 'STRONG_NO';

/** 批量上传项状态 */
export type BatchUploadItemStatus = 'QUEUED' | 'DUPLICATE' | 'FAILED';

/** Webhook 事件类型 */
export type WebhookEventType =
  | 'resume.uploaded'
  | 'resume.parse_completed'
  | 'resume.parse_failed'
  | 'candidate.created'
  | 'candidate.updated'
  | 'application.submitted'
  | 'application.status_changed'
  | 'interview.scheduled'
  | 'interview.completed'
  | 'interview.cancelled'
  | 'system.error'
  | 'system.maintenance';

// ==================== Auth 模块 ====================

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  role?: 'HR' | 'INTERVIEWER';
  verificationCode: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SendVerificationCodeRequest {
  email: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: UserInfo;
}

export interface UserInfo {
  userId: number;
  username: string;
  email: string;
  role: UserRole;
  dailyAiQuota: number;
  todayAiUsed: number;
}

// ==================== Job 模块 ====================

export interface CreateJobRequest {
  title: string;
  department?: string;
  description: string;
  requirements: string;
  requiredSkills?: string[];
  salaryMin: number;
  salaryMax: number;
  experienceMin?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
}

export interface UpdateJobRequest {
  id: number;
  title?: string;
  department?: string;
  description?: string;
  requirements?: string;
  requiredSkills?: string[];
  salaryMin?: number;
  salaryMax?: number;
  experienceMin?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
}

export interface JobQueryRequest {
  keyword?: string;
  department?: string;
  jobType?: string;
  education?: string;
  experienceMin?: number;
  salaryMin?: number;
  status?: JobStatus;
  pageNum?: number;
  pageSize?: number;
  orderBy?: 'created_at' | 'salary_max' | 'view_count';
  orderDirection?: 'asc' | 'desc';
}

export interface JobResponse {
  id: number;
  title: string;
  department: string;
  description: string;
  requirements: string;
  requiredSkills: string[];
  salaryMin: number;
  salaryMax: number;
  salaryRange: string;
  experienceMin: number;
  experienceMax: number;
  experienceRange: string;
  education: string;
  jobType: string;
  status: JobStatus;
  statusDesc: string;
  creatorId: number;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
}

// ==================== Resume 模块 ====================

export interface ResumeUploadResponse {
  taskId: string;
  resumeId: number;
  duplicated: boolean;
  message: string;
}

export interface BatchUploadResponse {
  totalCount: number;
  successCount: number;
  failedCount: number;
  items: BatchUploadItem[];
}

export interface BatchUploadItem {
  taskId: string | null;
  resumeId: number | null;
  fileName: string;
  status: BatchUploadItemStatus;
  message: string;
}

export interface TaskStatusResponse {
  status: TaskStatus;
  resumeId: number | null;
  candidateId: number | null;
  errorMessage: string | null;
  progress: number | null;
}

export interface Resume {
  id: number;
  userId: number;
  fileName: string;
  filePath: string;
  fileUrl: string;
  fileSize: number;
  fileHash: string;
  fileType: string;
  status: ResumeStatus;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Candidate 模块 ====================

export interface CandidateResponse {
  id: number;
  resumeId: number;
  name: string;
  phone: string;              // 脱敏
  email: string;              // 脱敏
  gender: string;
  age: number;
  education: string;
  school: string;
  major: string;
  graduationYear: number;
  workYears: number;
  currentCompany: string;
  currentPosition: string;
  skills: string[];
  workExperience: WorkExperienceItem[];
  projectExperience: ProjectExperienceItem[];
  selfEvaluation: string;
  confidenceScore: number;
  aiSummary: string;
  parsedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkExperienceItem {
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ProjectExperienceItem {
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  technologies: string[];
}

export interface CandidateUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  workYears?: number;
  currentCompany?: string;
  currentPosition?: string;
  skills?: string[];
  selfEvaluation?: string;
}

export interface CandidateQueryParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  education?: string;
  skill?: string;
  minWorkYears?: number;
  maxWorkYears?: number;
  currentPosition?: string;
}

// ==================== Smart Search 模块 ====================

export interface SmartSearchRequest {
  query: string;
  topK?: number;
  minScore?: number;
}

export interface SmartSearchResponse {
  query: string;
  totalMatches: number;
  candidates: MatchedCandidate[];
}

export interface MatchedCandidate {
  candidateId: number;
  name: string;
  matchScore: number;
  currentPosition: string;
  currentCompany: string;
  education: string;
  workYears: number;
  skills: string[];
  aiSummary: string;
}

// ==================== Application 模块 ====================

export interface CreateApplicationRequest {
  jobId: number;
  candidateId: number;
  hrNotes?: string;
}

export interface UpdateApplicationStatusRequest {
  status: 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';
  hrNotes?: string;
}

export interface ApplicationResponse {
  id: number;
  jobId: number;
  jobTitle: string;
  candidateId: number;
  candidateName: string;
  matchScore: number;
  matchReasons: string[];
  matchCalculatedAt: string;
  status: ApplicationStatus;
  statusDesc: string;
  hrNotes: string;
  appliedAt: string;
  updatedAt: string;
}

export interface ApplicationQueryRequest {
  pageNum?: number;
  pageSize?: number;
  jobId?: number;
  candidateId?: number;
  status?: ApplicationStatus;
  orderBy?: 'applied_at' | 'match_score' | 'updated_at';
  orderDirection?: 'asc' | 'desc';
}

// ==================== Interview 模块 ====================

export interface ScheduleInterviewRequest {
  applicationId: number;
  interviewerId: number;
  round?: number;
  interviewType: InterviewType;
  scheduledAt: string;
  durationMinutes?: number;
}

export interface SubmitFeedbackRequest {
  score: number;
  feedback: string;
  recommendation: Recommendation;
}

export interface InterviewResponse {
  id: number;
  applicationId: number;
  interviewerId: number;
  interviewerName: string;
  round: number;
  interviewType: InterviewType;
  interviewTypeDesc: string;
  scheduledAt: string;
  durationMinutes: number;
  status: InterviewStatus;
  statusDesc: string;
  feedback: string | null;
  score: number | null;
  recommendation: Recommendation | null;
  recommendationDesc: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== Webhook 模块 ====================

export interface WebhookCreateRequest {
  url: string;
  events: WebhookEventType[];
  description?: string;
}

export interface WebhookResponse {
  id: number;
  url: string;
  events: string[];
  description: string;
  enabled: boolean;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  createdAt: string;
  updatedAt: string;
  secretHint: string;
}

export interface WebhookPayload {
  eventId: string;
  eventType: WebhookEventType;
  timestamp: string;
  data: Record<string, any>;
  signature: string;
  version: string;
}
```

---

## API 端点总表 (40个)

| # | 方法 | 路径 | Auth | Request | Response |
|---|------|------|:----:|---------|----------|
| **Auth (5)** | | | | | |
| 1 | POST | `/auth/register` | ❌ | `RegisterRequest` | `Result<null>` |
| 2 | POST | `/auth/login` | ❌ | `LoginRequest` | `Result<LoginResponse>` |
| 3 | POST | `/auth/send-verification-code` | ❌ | `SendVerificationCodeRequest` | `Result<null>` |
| 4 | POST | `/auth/refresh` | ❌ | `RefreshTokenRequest` | `Result<LoginResponse>` |
| 5 | GET | `/auth/test` | ✅ | — | `Result<TestAuthResponse>` |
| **Job (8)** | | | | | |
| 6 | POST | `/jobs` | ✅ | `CreateJobRequest` | `Result<number>` |
| 7 | PUT | `/jobs` | ✅ | `UpdateJobRequest` | `Result<null>` |
| 8 | GET | `/jobs/{id}` | ❌ | — | `Result<JobResponse>` |
| 9 | GET | `/jobs` | ❌ | `JobQueryRequest` (query) | `Result<Page<JobResponse>>` |
| 10 | POST | `/jobs/{id}/publish` | ✅ | — | `Result<null>` |
| 11 | POST | `/jobs/{id}/close` | ✅ | — | `Result<null>` |
| 12 | DELETE | `/jobs/{id}` | ✅ | — | `Result<null>` |
| 13 | GET | `/jobs/hot` | ❌ | `?limit=10` | `Result<JobResponse[]>` |
| **Resume (5)** | | | | | |
| 14 | POST | `/resumes/upload` | ✅ | `multipart: file` | `Result<ResumeUploadResponse>` |
| 15 | POST | `/resumes/batch-upload` | ✅ | `multipart: files[]` | `Result<BatchUploadResponse>` |
| 16 | GET | `/resumes/tasks/{taskId}` | ✅ | — | `Result<TaskStatusResponse>` |
| 17 | GET | `/resumes/{id}` | ✅ | — | `Result<Resume>` |
| 18 | GET | `/resumes` | ✅ | `?page=1&size=10` | `Result<Page<Resume>>` |
| **Candidate (5)** | | | | | |
| 19 | GET | `/candidates/resume/{resumeId}` | ✅ | — | `Result<CandidateResponse>` |
| 20 | GET | `/candidates/{id}` | ✅ | — | `Result<CandidateResponse>` |
| 21 | PUT | `/candidates/{id}` | ✅ | `CandidateUpdateRequest` | `Result<CandidateResponse>` |
| 22 | DELETE | `/candidates/{id}` | ✅ | — | `Result<null>` |
| 23 | GET | `/candidates` | ✅ | `CandidateQueryParams` (query) | `Result<Page<CandidateResponse>>` |
| **Smart Search (1)** | | | | | |
| 24 | POST | `/candidates/smart-search` | ✅ | `SmartSearchRequest` | `Result<SmartSearchResponse>` |
| **Application (6)** | | | | | |
| 25 | POST | `/applications` | ✅ | `CreateApplicationRequest` | `Result<number>` |
| 26 | PUT | `/applications/{id}/status` | ✅ | `UpdateApplicationStatusRequest` | `Result<null>` |
| 27 | GET | `/applications/{id}` | ✅ | — | `Result<ApplicationResponse>` |
| 28 | GET | `/applications/job/{jobId}` | ✅ | `?pageNum=1&pageSize=10` | `Result<Page<ApplicationResponse>>` |
| 29 | GET | `/applications/candidate/{candidateId}` | ✅ | `?pageNum=1&pageSize=10` | `Result<Page<ApplicationResponse>>` |
| 30 | GET | `/applications` | ✅ | `ApplicationQueryRequest` (query) | `Result<Page<ApplicationResponse>>` |
| **Interview (5)** | | | | | |
| 31 | POST | `/interviews` | ✅ | `ScheduleInterviewRequest` | `Result<number>` |
| 32 | PUT | `/interviews/{id}/feedback` | ✅ | `SubmitFeedbackRequest` | `Result<null>` |
| 33 | POST | `/interviews/{id}/cancel` | ✅ | — | `Result<null>` |
| 34 | GET | `/interviews/{id}` | ✅ | — | `Result<InterviewResponse>` |
| 35 | GET | `/interviews/application/{applicationId}` | ✅ | — | `Result<InterviewResponse[]>` |
| **Webhook (4)** | | | | | |
| 36 | POST | `/webhooks` | ✅ | `WebhookCreateRequest` | `Result<WebhookResponse>` |
| 37 | GET | `/webhooks` | ✅ | — | `Result<WebhookResponse[]>` |
| 38 | DELETE | `/webhooks/{id}` | ✅ | — | `Result<null>` |
| 39 | POST | `/webhooks/{id}/test` | ✅ | — | `Result<boolean>` |

> 所有路径前缀 `/api/v1`，总计 **39 个端点**（含 auth/test 共 40 个）
