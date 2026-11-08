<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>测试用例 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <div>
            <el-input v-model="keyword" placeholder="搜索 名称/方法/URL" clearable size="small" style="width:200px;margin-right:8px" />
            <el-select v-model="tagFilter" multiple filterable collapse-tags collapse-tags-tooltip placeholder="按标签" clearable size="small" style="width:170px;margin-right:8px">
              <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
            </el-select>
            <el-select v-model="groupBy" class="grp-select" size="small" style="width:126px;margin-right:8px" title="分组折叠展示：按用例的第一个标签 / 按协议分组" @change="onGroupByChange">
              <el-option label="不分组" value="" />
              <el-option label="按标签分组" value="tag" />
              <el-option label="按协议分组" value="protocol" />
            </el-select>
            <template v-if="groupBy">
              <el-button size="small" style="margin-right:8px" @click="setAllGroups(false)">展开全部</el-button>
              <el-button size="small" style="margin-right:8px" @click="setAllGroups(true)">折叠全部</el-button>
            </template>
            <el-button @click="openVariables">变量/常量</el-button>
            <el-button @click="triggerImport" title="导入 JSON（单条对象或数组）">导入</el-button>
            <el-button @click="exportAll" title="导出当前项目全部用例为 JSON">导出全部</el-button>
            <el-button type="success" :disabled="!selected.length" :loading="batchRunning" @click="onBatchRun">批量运行<span v-if="selected.length">（{{ selected.length }}）</span></el-button>
            <el-dropdown trigger="click" :disabled="!selected.length" @command="onBatchStatus">
              <el-button :disabled="!selected.length">批量状态 ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="enable">启用</el-dropdown-item>
                  <el-dropdown-item command="disable">停用</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button type="danger" :icon="Delete" :disabled="!selected.length" :loading="batchRemoving" @click="onBatchRemove">批量删除<span v-if="selected.length">（{{ selected.length }}）</span></el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建用例</el-button>
          </div>
          <input ref="importInput" type="file" accept=".json,application/json" style="display:none" @change="onImportFile" />
        </div>
      </template>
      <el-table
        ref="tableRef"
        :data="tableData"
        row-key="id"
        v-loading="loading"
        border
        stripe
        size="small"
        :span-method="spanMethod"
        :row-class-name="rowClassName"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="42" :reserve-selection="true" :selectable="isSelectableRow" />
        <template #empty>
          <el-empty description="暂无用例">
            <el-button type="primary" size="small" @click="openCreate">新建用例</el-button>
          </el-empty>
        </template>
        <el-table-column prop="name" label="名称" width="160">
          <template #default="{ row }">
            <!-- 分组标题行：可折叠 + 可整组勾选 -->
            <div v-if="row._group" class="grp-head">
              <el-icon class="grp-caret" :title="row.collapsed ? '展开' : '折叠'" @click.stop="toggleGroup(row)">
                <CaretBottom v-if="!row.collapsed" />
                <CaretRight v-else />
              </el-icon>
              <span class="grp-name">{{ row.groupName }}</span>
              <el-tag size="small" type="info" effect="plain">{{ row.count }} 条</el-tag>
              <el-checkbox
                :model-value="groupAllSelected(row)"
                :indeterminate="groupPartSelected(row)"
                @change="(v) => toggleGroupSelect(row, v)"
              >全选</el-checkbox>
            </div>
            <span v-else>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="170">
          <template #default="{ row }">
            <el-tag v-for="t in parseArr(row.tags)" :key="t" size="small" type="info" effect="plain" style="margin:1px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="请求" show-overflow-tooltip>
          <template #default="{ row }"><el-tag size="small" :type="row.protocol === 'TCP' ? 'warning' : mTag(row.method)">{{ row.protocol === 'TCP' ? 'TCP' : row.method }}</el-tag> <span class="mono">{{ row.url }}</span></template>
        </el-table-column>
        <el-table-column label="模式" width="90">
          <template #default="{ row }">{{ row.mode === 'direct' ? '浏览器直发' : '后端转发' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="400">
          <template #default="{ row }">
            <el-button size="small" type="success" link @click="onRun(row)">运行</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link @click="onClone(row)">复制</el-button>
            <el-button size="small" type="info" link @click="openHistory(row)">历史</el-button>
            <el-button size="small" link @click="exportOne(row)">导出</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑/新建 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑用例' : '新建用例'" width="900px" top="3vh">
      <el-form :model="form" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="10"><el-form-item label="名称"><el-input v-model="form.name" placeholder="如：查询用户" /></el-form-item></el-col>
          <el-col :span="14">
            <el-form-item label="模式" label-width="56px">
              <el-radio-group v-model="form.mode">
                <el-radio label="proxy">后端转发（推荐，任意目标）</el-radio>
                <el-radio label="direct">浏览器直发（受 CORS）</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="协议" label-width="56px">
          <el-radio-group v-model="form.protocol">
            <el-radio label="HTTP">HTTP</el-radio>
            <el-radio label="TCP">TCP</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="标签" label-width="56px">
          <el-select v-model="form.tagsArr" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="回车添加，如 smoke / P0 / slow" style="width:100%">
            <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>

        <!-- ===== HTTP ===== -->
        <template v-if="form.protocol === 'HTTP'">
        <el-divider content-position="left">请求</el-divider>
        <div class="hint">
          URL/请求头/查询/Body 支持 <span class="mono">${var.变量名}</span> 与函数（如 <span class="mono">${uuid()}</span>、<span class="mono">${int(1,100)}</span>，函数仅「后端转发」模式求值）。
          点变量名复制引用：
          <el-tag v-for="v in variables" :key="v.id" size="small" style="margin:0 4px;cursor:pointer" @click="copyVar(v)">{{ v.name }}</el-tag>
          <span v-if="!variables.length" class="muted">（先到「变量/常量」添加）</span>
          ；也可以在任意参数右侧点「引用」直接插入到光标处。
        </div>
        <el-row :gutter="8" style="margin-bottom:6px">
          <el-col :span="4">
            <el-select v-model="form.method">
              <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS']" :key="m" :label="m" :value="m" />
            </el-select>
          </el-col>
          <el-col :span="17"><el-input :ref="(el) => setRowRef(el, 'url')" v-model="form.url" placeholder="http://host:port/path" /></el-col>
          <el-col :span="3"><ParamRefMenu :variables="variables" :env-vars="envVars" :data-keys="dataKeys" @insert="(t) => insertRef(t, 'url')" /></el-col>
        </el-row>
        <div class="kv-title">请求头</div>
        <div v-for="(h, i) in form.headers" :key="'h'+i" class="kv-row">
          <el-input v-model="h.k" placeholder="Header 名" style="width:32%" />
          <el-input :ref="(el) => setRowRef(el, 'h' + i)" v-model="h.v" placeholder="Header 值" style="width:44%" />
          <ParamRefMenu :variables="variables" :env-vars="envVars" :data-keys="dataKeys" @insert="(t) => insertRef(t, 'h' + i)" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.headers.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.headers.push({ k: '', v: '' })">加请求头</el-button>
        <el-dropdown trigger="click" @command="addCommonHeader">
          <el-button size="small" link type="primary">常用头 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="json">Content-Type: application/json</el-dropdown-item>
              <el-dropdown-item command="accept">Accept: application/json</el-dropdown-item>
              <el-dropdown-item command="auth">Authorization: Bearer $&#123;var.token&#125;</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <div class="kv-title" style="margin-top:10px">查询参数（Query）</div>
        <div v-for="(q, i) in form.query" :key="'q'+i" class="kv-row">
          <el-input v-model="q.k" placeholder="参数名" style="width:32%" />
          <el-input :ref="(el) => setRowRef(el, 'q' + i)" v-model="q.v" placeholder="参数值" style="width:44%" />
          <ParamRefMenu :variables="variables" :env-vars="envVars" :data-keys="dataKeys" @insert="(t) => insertRef(t, 'q' + i)" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.query.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.query.push({ k: '', v: '' })">加参数</el-button>

        <div class="kv-title" style="margin-top:10px">
          请求体
          <el-radio-group v-model="form.bodyType" size="small" style="margin-left:10px">
            <el-radio-button label="none">none</el-radio-button>
            <el-radio-button label="form-data">form-data</el-radio-button>
            <el-radio-button label="x-www-form-urlencoded">x-www-form-urlencoded</el-radio-button>
            <el-radio-button label="raw">raw</el-radio-button>
            <el-radio-button label="binary">binary</el-radio-button>
          </el-radio-group>
        </div>

        <!-- form-data / x-www-form-urlencoded：键值表 -->
        <template v-if="form.bodyType === 'form-data' || form.bodyType === 'x-www-form-urlencoded'">
          <div v-for="(r, i) in form.formRows" :key="'br'+i" class="kv-row">
            <el-input v-model="r.k" placeholder="参数名" style="width:24%" />
            <el-select v-if="form.bodyType === 'form-data'" v-model="r.type" style="width:90px">
              <el-option label="文本" value="text" /><el-option label="文件" value="file" />
            </el-select>
            <el-input v-if="form.bodyType !== 'form-data' || r.type !== 'file'" :ref="(el) => setRowRef(el, 'br' + i)" v-model="r.v" placeholder="参数值（支持 ${var.x}）" style="flex:1" />
            <template v-else>
              <input type="file" class="file-input" @change="(e) => onPickFile(e, r)" />
              <span v-if="r.fileName" class="muted file-name">{{ r.fileName }}</span>
            </template>
            <ParamRefMenu v-if="form.bodyType !== 'form-data' || r.type !== 'file'" :variables="variables" :env-vars="envVars" :data-keys="dataKeys" @insert="(t) => insertRef(t, 'br' + i)" />
            <el-button :icon="Delete" circle size="small" type="danger" @click="form.formRows.splice(i,1)" />
          </div>
          <el-button size="small" :icon="Plus" @click="addFormRow">加参数</el-button>
        </template>

        <!-- raw：Content-Type + 文本 + 函数市场 -->
        <template v-else-if="form.bodyType === 'raw'">
          <div class="kv-row" style="margin-bottom:6px">
            <span class="muted" style="margin-right:6px">Content-Type</span>
            <el-select v-model="form.bodyContentType" placeholder="选择或输入" filterable allow-create style="width:260px">
              <el-option label="application/json" value="application/json" />
              <el-option label="text/xml" value="text/xml" />
              <el-option label="text/plain" value="text/plain" />
              <el-option label="text/html" value="text/html" />
              <el-option label="application/javascript" value="application/javascript" />
            </el-select>
            <ParamRefMenu :variables="variables" :env-vars="envVars" :data-keys="dataKeys" @insert="(t) => insertRef(t, 'body')" />
            <span class="muted">插入变量/函数到请求体</span>
          </div>
          <el-row :gutter="8">
            <el-col :span="17">
              <textarea ref="bodyArea" v-model="form.body" class="body-area" rows="4" spellcheck="false" placeholder='{"key":"${var.x}"}'></textarea>
            </el-col>
            <el-col :span="7">
              <div class="fn-panel"><div class="fn-title">函数市场 · 插入光标处</div><FunctionMarketSidebar :project-id="proj.id" @insert="insertBodyFn" /></div>
            </el-col>
          </el-row>
        </template>

        <!-- binary：单文件原始字节 -->
        <template v-else-if="form.bodyType === 'binary'">
          <div class="kv-row" style="margin-bottom:6px">
            <input type="file" class="file-input" style="flex:1" @change="onPickBinary" />
            <el-button v-if="form.binaryFile.fileName" :icon="Delete" circle size="small" type="danger" @click="form.binaryFile = {}" />
          </div>
          <div v-if="form.binaryFile.fileName" class="muted">{{ form.binaryFile.fileName }} · {{ form.binaryFile.contentType || 'application/octet-stream' }} · {{ fileKb(form.binaryFile.dataB64) }}KB</div>
          <div v-else class="hint">选择文件作为原始字节请求体（Content-Type 默认 application/octet-stream；限 2MB）。</div>
        </template>

        <el-divider content-position="left">curl 导入</el-divider>
        <el-input v-model="curlText" type="textarea" :rows="2" placeholder="粘贴 curl 命令，点解析自动填充上方请求" />
        <el-button size="small" type="primary" style="margin-top:6px" @click="onImportCurl">解析 curl</el-button>

        </template>

        <!-- ===== TCP ===== -->
        <template v-else>
          <el-divider content-position="left">TCP 请求</el-divider>
          <div class="hint">
            目标 host:port；请求字段为 JSON 对象（值支持 <span class="mono">${var.x}</span>）。可从本项目 TCP 监听器一键填充帧/格式配置。
            响应按报文格式解析为字段，用 JSONPath 断言（如 <span class="mono">$.respCode</span>）。
          </div>
          <el-row :gutter="8" style="margin-bottom:6px">
            <el-col :span="12"><el-input v-model="form.url" placeholder="localhost:9001" /></el-col>
            <el-col :span="12">
              <el-select v-model="listenerPick" placeholder="从 TCP 监听器填充配置" clearable filterable style="width:100%" @change="fillFromListener">
                <el-option v-for="l in tcpListeners" :key="l.id" :label="l.name + ' (' + l.port + ')'" :value="l.id" />
              </el-select>
            </el-col>
          </el-row>
          <div class="kv-title">请求字段（JSON 对象）</div>
          <div class="kv-row" style="margin-bottom:4px">
            <ParamRefMenu :variables="variables" :env-vars="envVars" :data-keys="dataKeys" @insert="(t) => insertRef(t, 'tcpBody')" />
            <span class="muted">插入变量/函数到请求字段</span>
          </div>
          <textarea ref="tcpBodyArea" v-model="form.body" class="body-area" rows="4" spellcheck="false" placeholder='{"transCode":"0200","serialNo":"${var.sn}","amount":"100.00"}'></textarea>
          <div class="kv-title" style="margin-top:10px">帧 / 格式配置</div>
          <div class="hint" style="margin:2px 0 8px">
            一键选帧切分方式与报文格式，参数自动生成 <span class="mono">tcp_config</span>；需要手工微调时勾选「高级」。
          </div>
          <TcpFrameConfig
            v-model:frame-config="tcpFrame"
            v-model:message-format="tcpFormat"
            v-model:format-config="tcpFormatCfg"
          />
          <div style="margin-top:8px">
            <el-checkbox v-model="tcpAdv" @change="onAdvChange">高级：手工编辑 tcp_config JSON</el-checkbox>
          </div>
          <textarea v-if="tcpAdv" v-model="form.tcpConfig" class="body-area" rows="6" spellcheck="false" placeholder='{"frameConfig":{"type":"length_field","lenBytes":4},"messageFormat":"json"}'></textarea>
          <div v-else class="kv-title" style="margin-top:8px">生成的 tcp_config</div>
          <pre v-if="!tcpAdv" class="cfg-preview">{{ form.tcpConfig || '（空）' }}</pre>
          <div class="hint" style="margin-top:4px">
            短连接一问一答；length_field 自动前置大端长度头，delimiter 追加分隔符，fixed 定长，close_end 原样。<br />
            响应按 frameConfig <b>精确读取一帧</b>（不再等连接关闭）。<br />
            TCP 无响应头/状态码，header / status 类断言不适用。
          </div>
        </template>

        <el-divider content-position="left">断言</el-divider>
        <div class="hint" style="margin:2px 0 8px">
          JSONPath 用 <span class="mono">$.data.id</span> 定位字段，支持 = / ≠ / &gt; / &lt; / ≥ / ≤ / exists；
          JSON Schema 填标准 Schema，如 <span class="mono">{"type":"object","required":["code"]}</span>。
        </div>
        <div v-for="(a, i) in form.assertions" :key="'a'+i" class="kv-row">
          <el-select v-model="a.type" style="width:150px">
            <el-option label="状态码 status" value="status" :disabled="isTcp" />
            <el-option label="响应体包含" value="bodyContains" />
            <el-option label="响应头 header" value="header" :disabled="isTcp" />
            <el-option label="JSONPath" value="jsonPath" />
            <el-option label="耗时<ms latencyLt" value="latencyLt" />
            <el-option label="响应大小>bytes" value="sizeGt" />
            <el-option label="响应头存在" value="headerExists" :disabled="isTcp" />
            <el-option label="JSON Schema" value="jsonSchema" />
          </el-select>
          <el-input v-if="['header','jsonPath','headerExists'].includes(a.type)" v-model="a.target" :placeholder="a.type === 'jsonPath' ? '$.data.id' : 'Header-Name'" style="width:22%" />
          <el-select v-else disabled style="width:22%" />
          <el-select v-if="['header','jsonPath'].includes(a.type)" v-model="a.op" style="width:90px">
            <el-option label="等于 =" value="eq" />
            <el-option label="包含 contains" value="contains" />
            <template v-if="a.type === 'jsonPath'">
              <el-option label="不等于 ≠" value="ne" />
              <el-option label="大于 >" value="gt" />
              <el-option label="小于 <" value="lt" />
              <el-option label="大于等于 ≥" value="ge" />
              <el-option label="小于等于 ≤" value="le" />
              <el-option label="存在 exists" value="exists" />
            </template>
          </el-select>
          <el-input v-if="a.op !== 'exists' && a.type !== 'headerExists'" v-model="a.expected" :placeholder="a.type === 'jsonSchema' ? 'JSON Schema' : (a.type === 'latencyLt' ? 'ms 阈值' : a.type === 'sizeGt' ? 'bytes 阈值' : '期望值')" style="width:30%" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.assertions.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.assertions.push(isTcp ? { type: 'jsonPath', target: '$', op: 'exists', expected: '' } : { type: 'status', target: '', op: 'eq', expected: '200' })">加断言</el-button>

        <el-divider content-position="left">数据驱动（可选）</el-divider>
        <div class="hint">
          填写数据行（JSON 数组），每行键值注入 <span class="mono">${var.键}</span>，逐行运行用例（仅「后端转发」）。留空=普通单跑。
          如 <span class="mono">[{"uid":10086},{"uid":10087}]</span>，URL 写 <span class="mono">/api/user/${var.uid}</span>。
        </div>
        <textarea v-model="form.dataSet" class="body-area" rows="4" spellcheck="false" placeholder='[{"uid":10086},{"uid":10087}]'></textarea>
      </el-form>
      <template #footer>
        <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;width:100%">
          <span style="color:#909399;font-size:12px">运行环境：</span>
          <el-select v-model="runEnv" placeholder="默认(不注入)" clearable size="small" style="width:170px">
            <el-option v-for="e in envs" :key="e.id" :label="e.name + (e.baseUrl ? ' · ' + e.baseUrl : '')" :value="e.id" />
          </el-select>
          <span style="flex:1"></span>
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="success" @click="onSend" :loading="sending">发送</el-button>
          <el-button type="primary" @click="onSave">保存</el-button>
          <el-button type="warning" @click="onRunData" :loading="dataSending" :disabled="form.mode !== 'proxy'">数据驱动运行</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 运行结果 -->
    <el-dialog v-model="resultVisible" title="运行结果" width="820px" top="3vh">
      <div v-if="result">
        <div style="margin-bottom:8px">
          <el-tag :type="result.passed ? 'success' : 'danger'">{{ result.passed ? '✓ 通过' : '✗ 失败' }}</el-tag>
          <el-tag v-if="result.httpStatus != null" :type="sTag(result.httpStatus)" style="margin-left:6px">HTTP {{ result.httpStatus }}</el-tag>
          <span v-if="result.costMs != null" class="muted">耗时 {{ result.costMs }}ms</span>
          <el-tag v-if="result.mode" size="small" style="margin-left:6px">{{ result.mode === 'direct' ? '浏览器直发' : '后端转发' }}</el-tag>
        </div>
        <el-alert v-if="result.error" type="error" :closable="false" :title="result.error" style="margin-bottom:8px" />
        <el-alert v-if="result.warning" type="warning" :closable="false" :title="result.warning" style="margin-bottom:8px" />
        <div v-if="result.request" class="kv-title">请求报文</div>
        <pre v-if="result.request" class="resp">{{ formatRequest(result.request) }}</pre>
        <div v-if="result.assertions && result.assertions.length" class="kv-title">断言</div>
        <el-table v-if="result.assertions && result.assertions.length" :data="result.assertions" size="small" border style="margin-bottom:8px">
          <el-table-column label="结果" width="64"><template #default="{ row }">{{ row.passed ? '✓' : '✗' }}</template></el-table-column>
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="target" label="目标" width="150" show-overflow-tooltip />
          <el-table-column label="期望 / 实际"><template #default="{ row }"><span class="muted">期望:</span> {{ row.expected }} <span class="muted">| 实际:</span> {{ row.actual }}</template></el-table-column>
        </el-table>
        <div v-if="respHeaders.length" class="kv-title">响应头</div>
        <pre v-if="respHeaders.length" class="resp">{{ respHeaders.map(h => h.k + ': ' + h.v).join('\n') }}</pre>
        <div v-if="result.body != null" class="kv-title">响应体</div>
        <ResponseBody v-if="result.body != null" :body="result.body" />
      </div>
    </el-dialog>

    <!-- 数据驱动运行结果 -->
    <el-dialog v-model="dataResultVisible" title="数据驱动运行结果" width="900px" top="3vh">
      <div v-if="dataResult">
        <div style="margin-bottom:8px">
          <el-tag :type="dataResult.passed ? 'success' : 'danger'">{{ dataResult.passed ? '✓ 全部通过' : '✗ 存在失败' }}</el-tag>
          <span class="muted" style="margin-left:8px">通过 {{ dataResult.passedCount }}/{{ dataResult.total }} 行</span>
        </div>
        <el-table :data="dataResultRows" size="small" border max-height="440">
          <el-table-column prop="_row" label="行" width="56" />
          <el-table-column label="数据行变量" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row._vars ? JSON.stringify(row._vars) : '' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="56"><template #default="{ row }">{{ row.passed ? '✓' : '✗' }}</template></el-table-column>
          <el-table-column prop="httpStatus" label="状态" width="56" />
          <el-table-column label="耗时" width="76"><template #default="{ row }">{{ row.costMs }}ms</template></el-table-column>
          <el-table-column prop="error" label="错误" width="120" show-overflow-tooltip />
          <el-table-column label="响应体" show-overflow-tooltip><template #default="{ row }">{{ row.body }}</template></el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="openDataRowDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 批量运行结果 -->
    <el-dialog v-model="batchResultVisible" title="批量运行结果" width="860px" top="3vh">
      <div v-if="batchResult">
        <div style="margin-bottom:8px">
          <el-tag :type="batchResult.passed ? 'success' : 'danger'">{{ batchResult.passed ? '✓ 全部通过' : '✗ 存在失败' }}</el-tag>
          <span class="muted" style="margin-left:8px">通过 {{ batchResult.passedCount }}/{{ batchResult.total }} · 失败 {{ batchResult.failedCount }} · 总耗时 {{ batchResult.costMs }}ms</span>
        </div>
        <el-table :data="batchResult.results" size="small" border max-height="480">
          <el-table-column label="用例" show-overflow-tooltip>
            <template #default="{ row }">{{ row.name || ('#' + row.id) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="64"><template #default="{ row }">{{ row.passed ? '✓' : '✗' }}</template></el-table-column>
          <el-table-column prop="httpStatus" label="状态" width="64" />
          <el-table-column label="耗时" width="80"><template #default="{ row }">{{ row.costMs != null ? row.costMs + 'ms' : '-' }}</template></el-table-column>
          <el-table-column label="错误 / 警告" min-width="220">
            <template #default="{ row }">
              <div v-if="row.error" style="color:#f56c6c;word-break:break-all">{{ row.error }}</div>
              <div v-else-if="row.warning" style="color:#e6a23c;word-break:break-all">{{ row.warning }}</div>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 运行历史 -->
    <el-dialog v-model="historyVisible" title="运行历史" width="780px">
      <el-table :data="history" size="small" border>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="结果" width="70"><template #default="{ row }">{{ row.passed === 1 ? '✓' : '✗' }}</template></el-table-column>
        <el-table-column prop="mode" label="模式" width="90" />
        <el-table-column prop="httpStatus" label="状态" width="70" />
        <el-table-column prop="costMs" label="耗时" width="80"><template #default="{ row }">{{ row.costMs }}ms</template></el-table-column>
        <el-table-column prop="error" label="错误" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openRunDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 运行记录详情（请求 / 返回报文） -->
    <el-dialog v-model="detailVisible" title="运行记录" width="820px" top="3vh">
      <div v-if="detail">
        <div style="margin-bottom:8px">
          <el-tag :type="detail.passed ? 'success' : 'danger'">{{ detail.passed ? '✓ 通过' : '✗ 失败' }}</el-tag>
          <el-tag v-if="detail.httpStatus != null" :type="sTag(detail.httpStatus)" style="margin-left:6px">HTTP {{ detail.httpStatus }}</el-tag>
          <span v-if="detail.costMs != null" class="muted">耗时 {{ detail.costMs }}ms</span>
          <span class="muted" style="margin-left:8px">{{ detail.time }} · {{ detail.mode }}</span>
        </div>
        <el-alert v-if="detail.error" type="error" :closable="false" :title="detail.error" style="margin-bottom:8px" />
        <div class="kv-title">请求报文</div>
        <pre class="resp">{{ detail.requestText || '（该记录未保存请求报文）' }}</pre>
        <div class="kv-title" style="margin-top:10px">返回报文</div>
        <pre v-if="detail.responseHeaderText" class="resp">{{ detail.responseHeaderText }}</pre>
        <ResponseBody v-if="detail.responseBody != null" :body="detail.responseBody" />
        <div v-if="detail.assertions && detail.assertions.length" class="kv-title" style="margin-top:10px">断言</div>
        <el-table v-if="detail.assertions && detail.assertions.length" :data="detail.assertions" size="small" border>
          <el-table-column label="结果" width="64"><template #default="{ row }">{{ row.passed ? '✓' : '✗' }}</template></el-table-column>
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="target" label="目标" width="150" show-overflow-tooltip />
          <el-table-column label="期望 / 实际"><template #default="{ row }"><span class="muted">期望:</span> {{ row.expected }} <span class="muted">| 实际:</span> {{ row.actual }}</template></el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 变量 / 常量 管理 -->
    <el-dialog v-model="varVisible" title="变量 / 常量（项目级）" width="660px">
      <div style="margin-bottom:8px">
        <span class="muted">在用例里用 ${var.name} 引用；值可为常量或含 ${...} 函数（运行时后端转发模式求值）</span>
        <el-button size="small" type="primary" :icon="Plus" style="float:right" @click="openVarCreate">新增</el-button>
      </div>
      <el-table :data="variables" size="small" border>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="varValue" label="值" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openVarEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onVarRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="varFormVisible" :title="varForm.id ? '编辑变量' : '新增变量'" width="460px" append-to-body>
        <el-form :model="varForm" label-width="64px">
          <el-form-item label="名称"><el-input v-model="varForm.name" :disabled="!!varForm.id" placeholder="如 host / token" /></el-form-item>
          <el-form-item label="值"><el-input v-model="varForm.varValue" placeholder="常量 或 含 ${...} 函数" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="varForm.remark" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="varFormVisible = false">取消</el-button>
          <el-button type="primary" @click="onVarSave">保存</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, CaretBottom, CaretRight } from '@element-plus/icons-vue'
import { api } from '../api'
import { createGuard } from '../api/client'
import { confirmAction } from '../utils/confirm'
import { useProjectStore } from '../store/project'
import { useTestCaseDraftStore } from '../store/testcaseDraft'
import { useEnvStore } from '../store/env'
import { parseCurl } from '../utils/curl'
import { copyText } from '../utils/clipboard'
import FunctionMarketSidebar from '../components/FunctionMarketSidebar.vue'
import ParamRefMenu from '../components/ParamRefMenu.vue'
import ResponseBody from '../components/ResponseBody.vue'
import TcpFrameConfig from '../components/TcpFrameConfig.vue'

const proj = useProjectStore()
const draftStore = useTestCaseDraftStore()
const envStore = useEnvStore()
const list = ref([])
const loading = ref(false)
const keyword = ref('')
const tagFilter = ref([])
const allTags = computed(() => {
  const s = new Set()
  for (const c of list.value) {
    for (const x of parseArr(c.tags)) s.add(x)
  }
  return Array.from(s).sort()
})
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const tf = tagFilter.value
  if (!k && !tf.length) return list.value
  return list.value.filter((t) => {
    if (k && !((t.name || '') + ' ' + (t.method || '') + ' ' + (t.url || '')).toLowerCase().includes(k)) return false
    if (tf.length) {
      const tags = parseArr(t.tags)
      if (!tf.some((f) => tags.includes(f))) return false
    }
    return true
  })
})

// ===== 分组折叠展示 =====
// 分组方式：'' 不分组 / 'tag' 按首个标签 / 'protocol' 按协议。
// 用「分组标题行 + 普通行」拼在同一张表里：标题行跨列（span-method），
// 折叠时只隐藏该组的普通行（不进 tableData），勾选状态用 reserve-selection 保留。
const groupBy = ref('')
const collapsedGroups = ref(new Set())
const tableRef = ref(null)

/** 表格列数（选择/名称/标签/请求/模式/操作）——分组标题行跨列用，新增列需同步 */
const TABLE_COL_COUNT = 6

function groupKeyOf(row) {
  if (groupBy.value === 'protocol') return row.protocol === 'TCP' ? 'TCP' : 'HTTP'
  const tags = parseArr(row.tags)
  return tags.length ? tags[0] : '__none__'
}
function groupNameOf(key) {
  if (groupBy.value === 'protocol') return key === 'TCP' ? 'TCP 用例' : 'HTTP 用例'
  return key === '__none__' ? '未分组' : key
}
const groupRows = computed(() => {
  if (!groupBy.value) return []
  const map = new Map()
  for (const r of filtered.value) {
    const key = groupKeyOf(r)
    if (!map.has(key)) map.set(key, { key, name: groupNameOf(key), items: [] })
    map.get(key).items.push(r)
  }
  // 分组排序：名称升序，「未分组」永远排最后
  return Array.from(map.values()).sort((a, b) => {
    if (a.key === '__none__') return 1
    if (b.key === '__none__') return -1
    return a.name.localeCompare(b.name, 'zh-Hans-CN')
  })
})
const tableData = computed(() => {
  if (!groupBy.value) return filtered.value
  const out = []
  for (const g of groupRows.value) {
    const isCollapsed = collapsedGroups.value.has(g.key)
    out.push({
      _group: true,
      id: 'g::' + g.key,
      groupKey: g.key,
      groupName: g.name,
      count: g.items.length,
      collapsed: isCollapsed,
      _items: g.items
    })
    if (!isCollapsed) out.push(...g.items)
  }
  return out
})
function spanMethod({ row, columnIndex }) {
  if (row && row._group) {
    if (columnIndex === 0) return { rowspan: 1, colspan: 1 }
    if (columnIndex === 1) return { rowspan: 1, colspan: TABLE_COL_COUNT - 1 }
    return { rowspan: 0, colspan: 0 } // 已被标题行合并，跳过
  }
  return { rowspan: 1, colspan: 1 }
}
function rowClassName({ row }) {
  return row && row._group ? 'group-row' : ''
}
function isSelectableRow(row) {
  return !(row && row._group)
}
function toggleGroup(row) {
  const s = collapsedGroups.value
  if (s.has(row.groupKey)) s.delete(row.groupKey)
  else s.add(row.groupKey)
}
function setAllGroups(collapsed) {
  const s = collapsedGroups.value
  s.clear()
  if (collapsed) for (const g of groupRows.value) s.add(g.key)
}
function onGroupByChange() {
  collapsedGroups.value.clear()
}
function groupSelectedCount(row) {
  let n = 0
  for (const it of row._items) if (selectedIds.value.has(it.id)) n++
  return n
}
function groupAllSelected(row) {
  return row._items.length > 0 && groupSelectedCount(row) === row._items.length
}
function groupPartSelected(row) {
  const n = groupSelectedCount(row)
  return n > 0 && n < row._items.length
}
function toggleGroupSelect(row, checked) {
  const t = tableRef.value
  if (!t) return
  for (const it of row._items) t.toggleRowSelection(it, checked)
}

const formVisible = ref(false)
const sending = ref(false)
const curlText = ref('')
const form = reactive({ id: null, name: '', protocol: 'HTTP', method: 'GET', url: '', headers: [], query: [], bodyType: 'none', body: '', bodyContentType: '', tcpConfig: '', tagsArr: [], formRows: [], binaryFile: {}, assertions: [], mode: 'proxy', status: 1, remark: '', dataSet: '' })

// TCP 用例没有响应头/状态码，header / status 类断言不适用（禁用对应选项）
const isTcp = computed(() => form.protocol === 'TCP')

// ===== TCP 帧 / 格式一键配置 =====
// 结构化配置是「表单态」，form.tcpConfig 是最终落库的 JSON 文本；两者由下面两个函数互转。
const tcpAdv = ref(false)          // 高级模式：直接手写 tcp_config JSON
const tcpFrame = ref(defaultTcpFrame())
const tcpFormat = ref('json')
const tcpFormatCfg = ref(null)

function defaultTcpFrame() {
  return {
    type: 'length_field', lenBytes: 4, offset: 0, adjustment: 0, initialStrip: 4,
    prefixHex: '', delimiter: '\\n', fixedLength: 64
  }
}

/** 结构化配置 → tcp_config JSON 文本 */
function buildTcpConfig() {
  const f = tcpFrame.value || defaultTcpFrame()
  let frameConfig
  if (f.type === 'delimiter') {
    frameConfig = { type: 'delimiter', value: f.delimiter || '\\n' }
  } else if (f.type === 'fixed') {
    frameConfig = { type: 'fixed', length: Number(f.fixedLength) || 64 }
  } else if (f.type === 'close_end') {
    frameConfig = { type: 'close_end' }
  } else {
    const offset = Number(f.offset) || 0
    const lenBytes = Number(f.lenBytes) || 4
    frameConfig = {
      type: 'length_field',
      lenBytes,
      offset,
      adjustment: Number(f.adjustment) || 0,
      initialStrip: f.initialStrip == null ? offset + lenBytes : Number(f.initialStrip)
    }
    if (f.prefixHex && String(f.prefixHex).trim()) frameConfig.prefix_hex = String(f.prefixHex).trim()
  }
  const cfg = tcpFormatCfg.value || {}
  let formatConfig = null
  if (tcpFormat.value === 'key_value') {
    formatConfig = { pairSep: cfg.pairSep || '&', kvSep: cfg.kvSep || '=' }
  } else if (tcpFormat.value === 'fixed_fields') {
    formatConfig = {
      fields: (Array.isArray(cfg.fields) ? cfg.fields : []).map((x) => ({
        name: x.name, len: Number(x.len) || 0
      }))
    }
  }
  return { frameConfig, messageFormat: tcpFormat.value || 'json', formatConfig }
}

/** tcp_config JSON 文本 → 结构化配置（解析不了就退回高级模式） */
function loadTcpConfigToFields() {
  const raw = (form.tcpConfig || '').trim()
  if (!raw) {
    tcpAdv.value = false
    tcpFrame.value = defaultTcpFrame()
    tcpFormat.value = 'json'
    tcpFormatCfg.value = null
    return
  }
  let m
  try {
    m = JSON.parse(raw)
  } catch (e) {
    tcpAdv.value = true // 不是合法 JSON：保留原文让用户手工改
    return
  }
  tcpAdv.value = false
  const f = defaultTcpFrame()
  // frameConfig 兼容「嵌套对象」与「JSON 字符串」两种写法
  let fc = m && m.frameConfig
  if (typeof fc === 'string') { try { fc = JSON.parse(fc) } catch (e) { fc = null } }
  if (fc && typeof fc === 'object') {
    if (fc.type) f.type = fc.type
    if (fc.lenBytes != null) f.lenBytes = Number(fc.lenBytes)
    if (fc.offset != null) f.offset = Number(fc.offset)
    if (fc.adjustment != null) f.adjustment = Number(fc.adjustment)
    if (fc.value != null) f.delimiter = String(fc.value)
    if (fc.length != null) f.fixedLength = Number(fc.length)
    if (fc.prefix_hex != null) f.prefixHex = String(fc.prefix_hex)
    // initialStrip 缺省时按「offset + 长度头」推导（与服务端解码器默认值一致）
    f.initialStrip = fc.initialStrip != null
      ? Number(fc.initialStrip)
      : (f.offset || 0) + (f.lenBytes || 4)
  }
  tcpFrame.value = f
  tcpFormat.value = (m && m.messageFormat) || 'json'
  let fcfg = m && m.formatConfig
  if (typeof fcfg === 'string') { try { fcfg = JSON.parse(fcfg) } catch (e) { fcfg = null } }
  tcpFormatCfg.value = fcfg && typeof fcfg === 'object' ? fcfg : null
}

/** 结构化配置变化 → 回写 tcp_config JSON（高级模式下不覆盖用户手写的内容） */
function syncTcpFieldsToConfig() {
  if (tcpAdv.value) return
  form.tcpConfig = JSON.stringify(buildTcpConfig(), null, 2)
}

/** 高级模式开关：关闭时把手写 JSON 解析回表单，解析不了则留在高级模式 */
function onAdvChange(v) {
  if (v) return
  const raw = (form.tcpConfig || '').trim()
  if (raw) {
    try { JSON.parse(raw) } catch (e) {
      ElMessage.warning('tcp_config 不是合法 JSON，无法切回表单模式')
      tcpAdv.value = true
      return
    }
  }
  loadTcpConfigToFields()
  syncTcpFieldsToConfig()
}

watch([tcpFrame, tcpFormat, tcpFormatCfg], syncTcpFieldsToConfig, { deep: true })

const resultVisible = ref(false)
const result = ref(null)
const respHeaders = computed(() => {
  if (!result.value || !result.value.headers) return []
  return Object.entries(result.value.headers).map(([k, v]) => ({ k, v }))
})

const historyVisible = ref(false)
const history = ref([])

// 运行记录详情（请求 / 返回报文）
const detailVisible = ref(false)
const detail = ref(null)

/**
 * 请求报文可读化：
 * HTTP → 请求行 + 请求头 + 请求体；TCP → 目标 + 帧配置 + 字段 + 线路字节(hex)。
 */
function formatRequest(req) {
  if (!req || typeof req !== 'object') return ''
  const lines = []
  if (req.protocol === 'TCP') {
    lines.push('TCP ' + (req.target || ''))
    if (req.frame) lines.push('frame: ' + req.frame)
    if (req.messageFormat) lines.push('format: ' + req.messageFormat)
  } else {
    lines.push((req.method || 'GET') + ' ' + (req.url || ''))
    const h = req.headers || {}
    Object.keys(h).forEach((k) => lines.push(k + ': ' + h[k]))
  }
  if (req.body) {
    lines.push('')
    lines.push(req.body)
  }
  if (req.raw) {
    lines.push('')
    lines.push('raw(' + (req.rawLength || 0) + ' 字节): ' + req.raw)
  }
  return lines.join('\n')
}

function safeParse(s) {
  if (!s) return null
  if (typeof s === 'object') return s
  try { return JSON.parse(s) } catch (e) { return null }
}

/** 打开某次运行记录：拉详情（请求/返回报文）并展示 */
async function openRunDetail(row) {
  try {
    const res = await api.testCases.runDetail(row.caseId, row.id)
    const log = res.data || {}
    const headers = safeParse(log.responseHeaders) || {}
    const headerText = Object.keys(headers).map((k) => k + ': ' + headers[k]).join('\n')
    detail.value = {
      time: log.createTime,
      passed: log.passed === 1,
      mode: log.mode,
      httpStatus: log.httpStatus,
      costMs: log.costMs,
      error: log.error,
      requestText: formatRequest(safeParse(log.requestInfo)),
      responseHeaderText: headerText,
      responseBody: log.responseBody,
      assertions: safeParse(log.assertionResult) || []
    }
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('读取运行记录失败：' + (e && e.message ? e.message : e))
  }
}

/**
 * 数据驱动某一行：运行响应里已带 request/response，无需再查库。
 * 断言里 type/target/expected/actual 字段与运行历史一致。
 */
function openDataRowDetail(row) {
  const resp = row.response || {}
  const headers = resp.headers || {}
  detail.value = {
    time: '第 ' + row._row + ' 行',
    passed: !!row.passed,
    mode: '数据驱动',
    httpStatus: row.httpStatus,
    costMs: row.costMs,
    error: row.error,
    requestText: formatRequest(row.request),
    responseHeaderText: Object.keys(headers).map((k) => k + ': ' + headers[k]).join('\n'),
    responseBody: resp.body != null ? resp.body : row.body,
    assertions: row.assertions || []
  }
  detailVisible.value = true
}

// 数据驱动运行
const dataResultVisible = ref(false)
const dataResult = ref(null)
const dataSending = ref(false)
// 运行环境按项目持久化（全局环境选择器）：跨页面/刷新保持，切换项目自动取该项目上次选择
const runEnv = computed({
  get: () => envStore.envId(proj.id),
  set: (v) => envStore.setEnvId(proj.id, v)
})
const envs = ref([])
const dataRows = computed(() => { try { return JSON.parse(form.dataSet || '[]') } catch (e) { return [] } })
const dataResultRows = computed(() => {
  if (!dataResult.value || !dataResult.value.results) return []
  return dataResult.value.results.map((r, i) => ({ ...r, _vars: dataRows.value[i], _row: i + 1 }))
})

// 变量/常量
const variables = ref([])
const varVisible = ref(false)
const varFormVisible = ref(false)
const varForm = reactive({ id: null, name: '', varValue: '', remark: '' })
const bodyArea = ref(null)
const tcpBodyArea = ref(null)

// ===== 快捷引用：把 ${var.x} / 函数片段插入到参数输入框的光标处 =====
// 每个输入框用函数 ref 记住自己（key 形如 url / h0 / q1 / br2），插入时按 key 定位原生元素。
const refMap = new Map()
function setRowRef(el, key) {
  if (el) refMap.set(key, el)
  else refMap.delete(key)
}
function nativeInput(key) {
  if (key === 'body') return bodyArea.value
  if (key === 'tcpBody') return tcpBodyArea.value
  const inst = refMap.get(key)
  if (!inst) return null
  const root = inst.$el || inst
  if (root && root.querySelector) {
    const el = root.querySelector('input, textarea')
    if (el) return el
  }
  return root && (root.tagName === 'INPUT' || root.tagName === 'TEXTAREA') ? root : null
}
/**
 * 在目标输入框光标处插入文本。
 * 直接改 DOM 再派发 input 事件，el-input / v-model 都会同步回模型，无需知道绑定关系。
 */
function insertRef(text, key) {
  const el = nativeInput(key)
  if (!el) { ElMessage.warning('未找到目标输入框，请先点击该输入框'); return }
  const cur = String(el.value == null ? '' : el.value)
  const start = typeof el.selectionStart === 'number' ? el.selectionStart : cur.length
  const end = typeof el.selectionEnd === 'number' ? el.selectionEnd : start
  const pos = start + text.length
  el.value = cur.slice(0, start) + text + cur.slice(end)
  el.dispatchEvent(new Event('input', { bubbles: true }))
  nextTick(() => {
    try { el.focus(); el.setSelectionRange(pos, pos) } catch (e) { /* 某些输入类型不支持选区 */ }
  })
}

// 数据驱动的数据行键名（每行注入 ${var.<键>}），供快捷引用插入
const dataKeys = computed(() => {
  const s = (form.dataSet || '').trim()
  if (!s) return []
  try {
    const arr = JSON.parse(s)
    if (!Array.isArray(arr)) return []
    const keys = []
    for (const row of arr) {
      if (row && typeof row === 'object' && !Array.isArray(row)) {
        for (const k of Object.keys(row)) if (!keys.includes(k)) keys.push(k)
      }
    }
    return keys
  } catch (e) { return [] }
})

// 当前选中环境的变量（环境变量与项目变量同名时以项目变量优先）
const envVars = computed(() => {
  const e = envs.value.find((x) => String(x.id) === String(runEnv.value))
  if (!e || !e.variables) return []
  try {
    const arr = JSON.parse(e.variables)
    return Array.isArray(arr) ? arr.filter((x) => x && x.name) : []
  } catch (err) { return [] }
})

const METHODS_TAG = { GET: 'success', POST: 'warning', PUT: 'primary', DELETE: 'danger' }
function mTag(m) { return METHODS_TAG[m] || 'info' }
function sTag(s) { return s >= 200 && s < 300 ? 'success' : s >= 400 ? 'danger' : 'info' }

// 切换项目时旧的取数请求可能后到，只让最新一次的响应回写，避免串项目的陈旧数据
const fetchCases = createGuard((pid) => api.testCases.list(pid))

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await fetchCases(proj.id)
    if (res) list.value = res.data || []
    // 勾选用 reserve-selection（折叠分组不丢勾选），但列表刷新后要清掉已失效的行
    await nextTick()
    if (tableRef.value) tableRef.value.clearSelection()
    selected.value = []
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', protocol: 'HTTP', method: 'GET', url: '', headers: [], query: [], bodyType: 'none', body: '', bodyContentType: '', tcpConfig: '', tagsArr: [], formRows: [], binaryFile: {}, assertions: [], mode: 'proxy', status: 1, remark: '', dataSet: '' })
  listenerPick.value = ''
  curlText.value = ''
  // TCP 帧/格式：重置为默认（4 字节大端长度头 + JSON），新建即可直接跑
  tcpAdv.value = false
  tcpFrame.value = defaultTcpFrame()
  tcpFormat.value = 'json'
  tcpFormatCfg.value = null
  syncTcpFieldsToConfig()
  formVisible.value = true
}

// 用例全量 → 编辑态（不含 formVisible/草稿重置，供 openEdit/onRun 复用）
function applyCaseToForm(tc) {
  form.id = tc.id
  form.name = tc.name; form.protocol = tc.protocol || 'HTTP'; form.method = tc.method || 'GET'; form.url = tc.url || ''
  form.tcpConfig = tc.tcpConfig || ''
  // TCP 帧/格式：把已存的 tcp_config 拆回表单（不是合法 JSON 时保留原文并进高级模式）
  loadTcpConfigToFields()
  syncTcpFieldsToConfig()
  form.tagsArr = parseArr(tc.tags)
  loadBody(tc)
  form.mode = tc.mode || 'proxy'; form.status = tc.status == null ? 1 : tc.status; form.remark = tc.remark || ''
  form.headers = parseArr(tc.headers); form.query = parseArr(tc.query); form.assertions = parseArr(tc.assertions)
  form.dataSet = tc.dataSet || ''
}

async function openEdit(row) {
  // 列表行已瘦身：有 id 时取全量；草稿(来自「请求日志→生成用例」，无 id)已是全量直接用
  let tc = row
  if (row && row.id) {
    const res = await api.testCases.get(row.id)
    tc = res.data
  }
  applyCaseToForm(tc)
  listenerPick.value = ''
  curlText.value = ''
  formVisible.value = true
}

function parseArr(s) { try { return JSON.parse(s || '[]') } catch (e) { return [] } }
function parseObj(s) { try { return JSON.parse(s || '{}') } catch (e) { return {} } }

const MAX_FILE = 2 * 1024 * 1024

// 按 bodyType 把后端存储反序列化到编辑态（legacy json/form 归一化为 raw）
function loadBody(row) {
  const bt = row.bodyType || 'none'
  form.body = row.body || ''
  form.bodyContentType = row.bodyContentType || ''
  form.formRows = []
  form.binaryFile = {}
  if (bt === 'json') { form.bodyType = 'raw'; form.bodyContentType = 'application/json' }
  else if (bt === 'form') { form.bodyType = 'raw'; form.bodyContentType = 'application/x-www-form-urlencoded' }
  else { form.bodyType = bt }
  if (form.bodyType === 'form-data' || form.bodyType === 'x-www-form-urlencoded') {
    form.formRows = parseArr(row.body)
  } else if (form.bodyType === 'binary') {
    form.binaryFile = parseObj(row.body)
  }
}

// 按编辑态序列化为后端 body 字段
function serializeBody() {
  if (form.bodyType === 'form-data' || form.bodyType === 'x-www-form-urlencoded') {
    return JSON.stringify((form.formRows || []).filter((r) => r.k))
  }
  if (form.bodyType === 'binary') {
    return form.binaryFile && form.binaryFile.dataB64 ? JSON.stringify(form.binaryFile) : ''
  }
  return form.body || ''
}
function serializeContentType() {
  if (form.bodyType === 'raw') return form.bodyContentType || ''
  if (form.bodyType === 'binary') return (form.binaryFile && form.binaryFile.contentType) || ''
  return ''
}

function addFormRow() {
  if (form.bodyType === 'form-data') {
    form.formRows.push({ k: '', v: '', type: 'text', fileName: '', contentType: '', dataB64: '' })
  } else {
    form.formRows.push({ k: '', v: '' })
  }
}

function readB64(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => { const d = String(r.result || ''); const i = d.indexOf(','); resolve(i >= 0 ? d.slice(i + 1) : d) }
    r.onerror = () => reject(r.error)
    r.readAsDataURL(file)
  })
}
async function onPickFile(e, row) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  if (f.size > MAX_FILE) { ElMessage.warning('文件不能超过 2MB'); e.target.value = ''; return }
  const b64 = await readB64(f)
  row.type = 'file'; row.fileName = f.name; row.contentType = f.type || 'application/octet-stream'; row.dataB64 = b64; row.v = ''
  e.target.value = ''
}
async function onPickBinary(e) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  if (f.size > MAX_FILE) { ElMessage.warning('文件不能超过 2MB'); e.target.value = ''; return }
  const b64 = await readB64(f)
  form.binaryFile = { fileName: f.name, contentType: f.type || 'application/octet-stream', dataB64: b64 }
  e.target.value = ''
}
function fileKb(b64) { if (!b64) return '0'; return String(Math.round(b64.length * 0.75 / 1024 * 10) / 10) }

// 浏览器直发(direct) 按类型构造 fetch 体
function buildDirectBody() {
  const t = form.bodyType
  if (t === 'none') return {}
  if (t === 'x-www-form-urlencoded') {
    const p = new URLSearchParams()
    ;(form.formRows || []).forEach((r) => { if (r.k) p.append(substituteVars(r.k), substituteVars(r.v || '')) })
    return { body: p }
  }
  if (t === 'form-data') {
    const fd = new FormData()
    ;(form.formRows || []).forEach((r) => {
      if (!r.k) return
      if (r.type === 'file' && r.dataB64) {
        const bytes = Uint8Array.from(atob(r.dataB64), (c) => c.charCodeAt(0))
        fd.append(substituteVars(r.k), new Blob([bytes], { type: r.contentType || 'application/octet-stream' }), substituteVars(r.fileName || 'file'))
      } else {
        fd.append(substituteVars(r.k), substituteVars(r.v || ''))
      }
    })
    return { body: fd }
  }
  if (t === 'binary') {
    if (form.binaryFile && form.binaryFile.dataB64) {
      const bytes = Uint8Array.from(atob(form.binaryFile.dataB64), (c) => c.charCodeAt(0))
      return { body: bytes, contentType: form.binaryFile.contentType || 'application/octet-stream' }
    }
    return {}
  }
  if (form.body) return { body: substituteVars(form.body), contentType: form.bodyContentType || undefined }
  return {}
}

function buildEntity() {
  return {
    id: form.id, name: form.name, protocol: form.protocol, method: form.method, url: form.url,
    bodyType: form.bodyType, body: serializeBody(), bodyContentType: serializeContentType(),
    tcpConfig: form.protocol === 'TCP' ? form.tcpConfig : '',
    tags: JSON.stringify(form.tagsArr || []),
    mode: form.mode, status: form.status, remark: form.remark,
    headers: JSON.stringify(form.headers.filter((h) => h.k)),
    query: JSON.stringify(form.query.filter((q) => q.k)),
    assertions: JSON.stringify(form.assertions),
    dataSet: (form.dataSet || '').trim()
  }
}

// TCP 目标校验：host:port，端口 1-65535（与后端一致，前端先拦一道）
function validateTcpTarget(target) {
  const idx = target.lastIndexOf(':')
  if (idx <= 0) return false
  const port = Number(target.slice(idx + 1).trim())
  return Number.isInteger(port) && port >= 1 && port <= 65535
}

function validateCase() {
  if (!form.name || !form.name.trim()) { ElMessage.warning('请输入用例名称'); return false }
  if (form.protocol === 'TCP') {
    if (!form.url || !form.url.trim()) { ElMessage.warning('请输入目标 host:port'); return false }
    if (!validateTcpTarget(form.url.trim())) { ElMessage.warning('目标格式应为 host:port，端口需在 1-65535'); return false }
    if (!form.body || !form.body.trim()) { ElMessage.warning('请输入请求字段（JSON）'); return false }
    try { JSON.parse(form.body) } catch (e) { ElMessage.warning('请求字段不是合法 JSON'); return false }
    if (!form.tcpConfig || !form.tcpConfig.trim()) { ElMessage.warning('请填写或从监听器填充帧/格式配置'); return false }
    try { JSON.parse(form.tcpConfig) } catch (e) { ElMessage.warning('tcp_config 不是合法 JSON'); return false }
  } else {
    if (!form.url || !form.url.trim()) { ElMessage.warning('请输入请求 URL'); return false }
  }
  return true
}

async function onSave() {
  if (!validateCase()) return
  const e = buildEntity()
  if (form.id) { await api.testCases.update(form.id, e) } else { const r = await api.testCases.create(proj.id, e); form.id = r.data.id }
  ElMessage.success('已保存'); formVisible.value = false; load()
}

async function saveSilently() {
  const e = buildEntity()
  if (form.id) { await api.testCases.update(form.id, e) } else { const r = await api.testCases.create(proj.id, e); form.id = r.data.id }
}

function onImportCurl() {
  if (!curlText.value.trim()) { ElMessage.warning('请粘贴 curl 命令'); return }
  try {
    const c = parseCurl(curlText.value)
    form.method = c.method; form.url = c.url; form.headers = c.headers
    form.bodyType = c.bodyType; form.body = c.body || ''; form.bodyContentType = c.bodyContentType || ''
    form.formRows = (c.bodyType === 'form-data' || c.bodyType === 'x-www-form-urlencoded') ? parseArr(c.body) : []
    form.binaryFile = {}
    ElMessage.success('已解析填充')
  } catch (e) { ElMessage.error('解析失败：' + e.message) }
}

function buildUrl(f) {
  let u = f.url || ''
  const qs = (f.query || []).filter((q) => q.k)
  if (!qs.length) return u
  const sep = u.includes('?') ? '&' : '?'
  return u + sep + qs.map((q) => encodeURIComponent(q.k) + '=' + encodeURIComponent(q.v || '')).join('&')
}

async function onSend() {
  if (!validateCase()) return
  sending.value = true
  try {
    if (form.mode === 'direct') {
      await runDirect()
    } else {
      await saveSilently()
      const res = await api.testCases.run(form.id, runEnv.value)
      showResult({ ...res.data, mode: 'proxy' })
    }
  } catch (e) {
    // proxy 模式错误已由全局拦截器提示；direct 模式走浏览器原生 fetch、不经拦截器，这里补一条
    if (form.mode === 'direct') {
      ElMessage.error('发送失败：' + (e?.message || ''))
    }
  } finally {
    sending.value = false
  }
}

async function onRun(row) {
  // 从列表直接运行：取全量后按 dataSet 判定 数据驱动/普通；不打开编辑弹窗(避免闪窗)
  const res = await api.testCases.get(row.id)
  applyCaseToForm(res.data)
  formVisible.value = false
  if ((form.dataSet || '').trim()) {
    await onRunData()
  } else {
    await onSend()
  }
}

async function onRunData() {
  if (!validateCase()) return
  let rows = []
  if ((form.dataSet || '').trim()) {
    try { rows = JSON.parse(form.dataSet) } catch (e) { ElMessage.error('数据行 JSON 解析失败：' + e.message); return }
    if (!Array.isArray(rows)) { ElMessage.error('数据行必须是 JSON 数组'); return }
  }
  dataSending.value = true
  try {
    await saveSilently()
    const res = await api.testCases.runData(form.id, runEnv.value)
    dataResult.value = res.data
    dataResultVisible.value = true
    formVisible.value = false
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    dataSending.value = false
  }
}

async function runDirect() {
  // 浏览器直发：仅支持 ${var.变量名} 客户端替换；DSL 函数需后端转发(proxy)
  const textFields = [form.url, ...(form.headers || []).map((h) => h.v), form.body, ...(form.formRows || []).map((r) => r.k + (r.v || ''))]
  if (textFields.some((f) => /\$\{(?!var\.)/.test(f || ''))) {
    showResult({ error: '浏览器直发不支持 DSL 函数（如 ${uuid()}），仅支持 ${var.变量名}。请改用「后端转发」模式。', passed: false, assertions: [], mode: 'direct' })
    return
  }
  const headers = {}
  form.headers.filter((h) => h.k).forEach((h) => { headers[substituteVars(h.k)] = substituteVars(h.v) })
  const opts = { method: form.method, headers }
  const bd = buildDirectBody()
  // GET/HEAD 不能带请求体：浏览器 fetch 会直接抛
  // "Request with GET/HEAD method cannot have body."，这里先剔除（与后端转发模式口径一致）
  const m = String(form.method || 'GET').toUpperCase()
  if (bd.body !== undefined && bd.body !== null) {
    if (m === 'GET' || m === 'HEAD') {
      showResult({ warning: '提示：GET/HEAD 请求不携带请求体，已忽略用例中配置的 body', httpStatus: 0, costMs: 0, headers: {}, body: '', assertions: [], passed: false, mode: 'direct' })
      return
    }
    opts.body = bd.body
    if (bd.contentType && !headers['Content-Type'] && !headers['content-type']) headers['Content-Type'] = bd.contentType
  }
  const t0 = Date.now()
  try {
    const resp = await fetch(substituteVars(buildUrl(form)), opts)
    const text = await resp.text()
    const rh = {}
    resp.headers.forEach((v, k) => { rh[k.toLowerCase()] = v })
    const ar = evalAssertionsClient(resp.status, rh, text, form.assertions)
    showResult({ httpStatus: resp.status, costMs: Date.now() - t0, headers: rh, body: text, assertions: ar, passed: ar.every((a) => a.passed), mode: 'direct' })
  } catch (e) {
    showResult({ error: '请求失败：可能是目标未允许跨域(CORS)，请改用「后端转发」模式。' + e.message, passed: false, assertions: [], mode: 'direct' })
  }
}

// ${var.name} 客户端替换（direct 模式用）
function substituteVars(text) {
  if (!text) return text
  return text.replace(/\$\{var\.([a-zA-Z_][\w]*)\}/g, (m, name) => {
    const v = variables.value.find((x) => x.name === name)
    return v ? (v.varValue || '') : m
  })
}

function insertBodyFn(text) {
  const cur = form.body || ''
  const el = bodyArea.value
  if (!el) { form.body = cur + text; return }
  const s = el.selectionStart || 0
  const e = el.selectionEnd || 0
  form.body = cur.slice(0, s) + text + cur.slice(e)
  nextTick(() => { el.focus(); el.selectionStart = el.selectionEnd = s + text.length })
}

function copyVar(v) {
  copyText('${var.' + v.name + '}')
}

// ===== 变量/常量 CRUD =====
const fetchVariables = createGuard((pid) => api.testVariables.list(pid))

async function loadVariables() {
  if (!proj.id) return
  try {
    const res = await fetchVariables(proj.id)
    if (res) variables.value = res.data || []
  } catch (e) { variables.value = [] }
}

async function openVariables() {
  await loadVariables()
  varVisible.value = true
}

function openVarCreate() {
  Object.assign(varForm, { id: null, name: '', varValue: '', remark: '' })
  varFormVisible.value = true
}

function openVarEdit(row) {
  Object.assign(varForm, { id: row.id, name: row.name, varValue: row.varValue || '', remark: row.remark || '' })
  varFormVisible.value = true
}

async function onVarSave() {
  if (!varForm.name.trim()) { ElMessage.warning('请填变量名'); return }
  if (varForm.id) { await api.testVariables.update(varForm.id, { varValue: varForm.varValue, remark: varForm.remark }) }
  else { await api.testVariables.create(proj.id, { ...varForm }) }
  ElMessage.success('已保存')
  varFormVisible.value = false
  loadVariables()
}

async function onVarRemove(row) {
  if (!(await confirmAction(`删除变量「${row.name}」？`, { title: '警告' }))) return
  await api.testVariables.remove(row.id)
  ElMessage.success('已删除')
  loadVariables()
}

function evalAssertionsClient(status, headers, body, assertions) {
  return (assertions || []).map((a) => {
    const type = a.type, target = (a.target || ''), op = a.op || 'eq', expected = a.expected || ''
    let actual = '', passed = false
    try {
      if (type === 'status') { actual = String(status); passed = actual === expected }
      else if (type === 'bodyContains') { actual = body || ''; passed = actual.includes(expected) }
      else if (type === 'header') { const hv = headers[(target || '').toLowerCase()] || ''; actual = hv; passed = op === 'contains' ? hv.includes(expected) : hv === expected }
      else if (type === 'jsonPath') {
        const v = getJsonPath(body, target)
        const disp = (v === undefined || v === null) ? '' : (typeof v === 'object' ? JSON.stringify(v) : String(v))
        actual = disp
        if (op === 'exists') { actual = (v === undefined || v === null) ? '(无)' : disp; passed = v !== undefined && v !== null }
        else if (op === 'contains') { passed = disp.includes(expected) }
        else if (op === 'ne') { passed = !smartEq(v, disp, expected) }
        else if (['gt', 'lt', 'ge', 'le'].includes(op)) { passed = cmpNum(v, expected, op) }
        else { passed = smartEq(v, disp, expected) } // eq
      }
    } catch (e) { actual = '解析失败' }
    return { type, target, op, expected, actual, passed }
  })
}

function getJsonPath(body, path) {
  if (!body || !path) return undefined
  let o = typeof body === 'string' ? JSON.parse(body) : body
  const parts = path.replace(/^\$\.?/, '').split(/\.|\[(\d+)\]/).filter((p) => p !== '' && p !== undefined)
  for (const p of parts) { if (o == null) return undefined; o = o[p] }
  return o
}

// 数值感知比较（与后端 evalAssertion 对齐）：两边均可解析为数字时按数值比
function toNum(v) {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  const s = String(v == null ? '' : v).trim()
  if (s === '') return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}
function smartEq(v, disp, expected) {
  const a = toNum(v), b = toNum(expected)
  if (a !== null && b !== null) return Math.abs(a - b) < 1e-9
  return disp === expected
}
function cmpNum(v, expected, op) {
  const a = toNum(v), b = toNum(expected)
  if (a === null || b === null) return false
  if (op === 'gt') return a > b
  if (op === 'lt') return a < b
  if (op === 'ge') return a >= b
  if (op === 'le') return a <= b
  return false
}

function showResult(r) { result.value = r; resultVisible.value = true }

async function openHistory(row) {
  const res = await api.testCases.runs(row.id)
  history.value = res.data || []
  historyVisible.value = true
}

async function onRemove(row) {
  if (!(await confirmAction(`删除用例「${row.name || '未命名'}」及其历史？`, { title: '警告' }))) return
  await api.testCases.remove(row.id)
  ElMessage.success('已删除'); load()
}

async function onClone(row) {
  // 一键复制：取全量 → 去主键/项目/时间戳 → 改名(副本) → 直接建副本
  try {
    const res = await api.testCases.get(row.id)
    const dup = sanitizeCase(res.data)
    dup.name = (res.data.name || '未命名') + '(副本)'
    await api.testCases.create(proj.id, dup)
    ElMessage.success('已复制为「' + dup.name + '」')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function addCommonHeader(cmd) {
  const map = {
    json: { k: 'Content-Type', v: 'application/json' },
    accept: { k: 'Accept', v: 'application/json' },
    auth: { k: 'Authorization', v: 'Bearer ${var.token}' }
  }
  const row = map[cmd]
  if (!row) return
  if (form.headers.some((h) => h.k === row.k)) { ElMessage.info(row.k + ' 已存在'); return }
  form.headers.push({ ...row })
}

const selected = ref([])
const selectedIds = computed(() => new Set((selected.value || []).map((r) => r.id)))
const batchRemoving = ref(false)
const batchRunning = ref(false)
const batchResultVisible = ref(false)
const batchResult = ref(null)
function onSelectionChange(rows) { selected.value = rows }
async function onBatchRemove() {
  if (!selected.value.length) return
  if (!(await confirmAction(`确认删除选中的 ${selected.value.length} 条用例及其历史？`, { title: '批量删除' }))) return
  batchRemoving.value = true
  try {
    await api.testCases.removeBatch(selected.value.map((r) => r.id))
    ElMessage.success('已删除 ' + selected.value.length + ' 条')
    selected.value = []
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    batchRemoving.value = false
  }
}

// 批量运行：统一经后端转发执行（含 TCP），环境变量只加载一次；逐条结果落历史
async function onBatchRun() {
  if (!selected.value.length) return
  batchRunning.value = true
  try {
    const res = await api.testCases.runBatch(proj.id, selected.value.map((r) => r.id), runEnv.value)
    batchResult.value = res.data
    batchResultVisible.value = true
    const r = res.data || {}
    ElMessage[r.passed ? 'success' : 'warning'](`批量运行完成：通过 ${r.passedCount}/${r.total}`)
    load() // 刷新运行历史计数等
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    batchRunning.value = false
  }
}

// 批量启用/停用
async function onBatchStatus(cmd) {
  if (!selected.value.length) return
  const status = cmd === 'enable' ? 1 : 0
  const label = cmd === 'enable' ? '启用' : '停用'
  if (!(await confirmAction(`确认${label}选中的 ${selected.value.length} 条用例？`, { title: '批量' + label }))) return
  try {
    await api.testCases.setBatchStatus(proj.id, selected.value.map((r) => r.id), status)
    ElMessage.success('已' + label + ' ' + selected.value.length + ' 条')
    selected.value = []
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

const fetchEnvs = createGuard((pid) => api.environments.list(pid))

async function loadEnvs() {
  if (!proj.id) { envs.value = []; return }
  try {
    const r = await fetchEnvs(proj.id)
    if (r) envs.value = r.data || []
  } catch (e) { envs.value = [] }
}

watch(() => proj.id, () => { load(); loadVariables(); loadEnvs() })
// TCP 监听器：填充帧/格式配置
const tcpListeners = ref([])
const listenerPick = ref('')
const fetchListeners = createGuard((pid) => api.listeners.list(pid))

async function loadListeners() {
  if (!proj.id) { tcpListeners.value = []; return }
  try {
    const r = await fetchListeners(proj.id)
    if (r) tcpListeners.value = r.data || []
  } catch (e) { tcpListeners.value = [] }
}
function fillFromListener(id) {
  const l = tcpListeners.value.find((x) => x.id === id)
  if (!l) return
  form.url = 'localhost:' + l.port
  let formatConfig = null
  try { formatConfig = l.messageFormatConfig ? JSON.parse(l.messageFormatConfig) : null } catch (e) { /* 留空 */ }
  // frameConfig 统一写成嵌套对象（后端同时兼容字符串写法）
  let frameConfig = {}
  try { frameConfig = l.frameConfig ? JSON.parse(l.frameConfig) : {} } catch (e) { frameConfig = {} }
  form.tcpConfig = JSON.stringify({
    frameConfig,
    messageFormat: l.messageFormat || 'json',
    formatConfig
  })
  // 同步到帧/格式表单：填完就能看到并微调每个参数
  loadTcpConfigToFields()
  syncTcpFieldsToConfig()
}

// 导入 / 导出（JSON）
const importInput = ref(null)
function sanitizeCase(c) {
  if (!c || typeof c !== 'object') return null
  const { id, projectId, createTime, updateTime, ...rest } = c
  return rest
}
function downloadJson(obj, filename) {
  const blob = new Blob([JSON.stringify(obj, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
async function exportOne(row) {
  const res = await api.testCases.get(row.id)
  const safe = (row.name || 'case').replace(/[^\w一-龥.-]/g, '_')
  downloadJson(sanitizeCase(res.data), `testcase-${safe}.json`)
}
async function exportAll() {
  if (!list.value.length) { ElMessage.warning('暂无用例可导出'); return }
  // 列表已瘦身，逐条取全量再导出（导出的是完整用例）
  const full = await Promise.all(list.value.map((r) => api.testCases.get(r.id).then((x) => x.data).catch(() => null)))
  const clean = full.filter(Boolean).map(sanitizeCase)
  downloadJson(clean, `testcases-${proj.code || proj.id || 'project'}.json`)
  ElMessage.success('已导出 ' + clean.length + ' 条')
}
function triggerImport() { if (importInput.value) importInput.value.click() }
async function onImportFile(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  try {
    const data = JSON.parse(await file.text())
    const arr = Array.isArray(data) ? data : [data]
    let ok = 0, fail = 0
    for (const c of arr) {
      const s = sanitizeCase(c)
      if (!s || !s.name || !s.name.trim()) { fail++; continue }
      try { await api.testCases.create(proj.id, s); ok++ } catch (err) { fail++ }
    }
    ElMessage[fail ? 'warning' : 'success'](`导入完成：成功 ${ok}，失败 ${fail}`)
    if (ok > 0) load()
  } catch (err) {
    ElMessage.error('导入失败：文件不是合法 JSON')
  }
  e.target.value = ''
}

onMounted(() => {
  load()
  loadVariables()
  loadEnvs()
  loadListeners()
  // 来自「请求日志 → 生成用例」的草稿：取出并打开新建弹窗（id 为空即新建）
  const draft = draftStore.take()
  if (draft) {
    openEdit(draft)
  }
})
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.muted { color: #909399; font-size: 12px; }
.kv-title { font-size: 13px; color: #606266; margin: 8px 0 4px; display: flex; align-items: center; }
.kv-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
.file-input { font-size: 12px; flex: 1; }
.file-name { margin-left: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 160px; }
.hint { font-size: 12px; color: #909399; margin: 4px 0 8px; line-height: 1.7; }
.fn-panel { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; }
.fn-title { font-size: 12px; color: #606266; margin-bottom: 6px; }
.body-area { width: 100%; font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 13px; padding: 8px;
  border: 1px solid #dcdfe6; border-radius: 4px; resize: vertical; box-sizing: border-box; }
.cfg-preview { background: #f5f7fa; padding: 8px; border-radius: 4px; max-height: 160px; overflow: auto; margin: 0;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
.resp { background: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 240px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
/* 分组标题行 */
.grp-head { display: flex; align-items: center; gap: 8px; }
.grp-caret { cursor: pointer; color: #909399; }
.grp-caret:hover { color: #409eff; }
.grp-name { font-weight: 600; }
:deep(.el-table__row.group-row > td.el-table__cell) { background: #f5f7fa; }
:deep(.el-table__row.group-row > td.el-table__cell .el-checkbox) { visibility: hidden; }
</style>
