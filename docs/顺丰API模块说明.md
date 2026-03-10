# 顺丰速运独立 API 模块说明

本系统已接入顺丰速运**速运类 API**，使用独立配置与独立接口，便于维护与扩展。

---

## 一、配置（application.yml）

```yaml
sf-express:
  partner-id: FCKZH1AEF3YE          # 顾客编码
  sandbox-checkword: VBYx...         # 沙箱校验码
  prod-checkword: 8lFz...            # 生产校验码
  prod-url: https://bspgw.sf-express.com/std/service
  hk-url: https://sfapi-hk.sf-express.com/std/service
  sandbox-url: https://sfapi-sbox.sf-express.com/std/service
  use-prod: false                    # true=生产，false=沙箱
```

- **沙箱**：`use-prod: false`，请求发往 `sandbox-url`，使用 `sandbox-checkword` 签名。
- **生产**：`use-prod: true`，请求发往 `prod-url`，使用 `prod-checkword` 签名。

---

## 二、已实现接口

### 1. 路由查询（EXP_RECE_SEARCH_ROUTES）

- **文档**：[路由查询接口-EXP_RECE_SEARCH_ROUTES](https://qiao.sf-express.com/Api/ApiDetails?apiServiceCode=EXP_RECE_SEARCH_ROUTES&category=1&apiClassify=3&interName=%E8%B7%AF%E7%94%B1%E6%9F%A5%E8%AF%A2%E6%8E%A5%E5%8F%A3-EXP_RECE_SEARCH_ROUTES)
- **用途**：按运单号 + 收件人手机后四位查询物流轨迹。
- **批量**：最多 10 个 `tracking_number`（当前实现为单号查询）；批量时 `checkPhoneNo` 为 `"0001,0002"` 与运单一一对应。

**官方请求示例（msgData）：**

- 单号：`language: "zh-CN"`, `trackingType: "1"`, `trackingNumber: ["444003077898"]`, `methodType: "1"`, `checkPhoneNo: "8805"`
- 批量尾号：`checkPhoneNo: "0001,0002"` 对应两个运单

**官方响应**：平台返回 `apiResultCode`（A1000=成功）、`apiErrorMsg`、`apiResultData`（**字符串**，需再解析 JSON 得到 `msgData.routeResps`）。本系统已按此解析。

**本系统调用方式：**

- **独立顺丰 API**：`GET /api/sf/routes?trackingNumber=SF5122068084664&checkPhoneNo=8805`
- **统一物流查询**：`GET /api/logistics/query?company=顺丰速运&trackingNumber=SF5122068084664&checkPhoneNo=8805`  
  内部会转发到顺丰模块，并支持按运单号从订单补全 `checkPhoneNo`。

### 2. 服务网点查询（EXP_RECE_QUERY_GIS_DEPARTMENT）

- **文档**：[顺丰服务网点查询接口-EXP_RECE_QUERY_GIS_DEPARTMENT](https://qiao.sf-express.com/Api/ApiDetails?apiServiceCode=EXP_RECE_QUERY_GIS_DEPARTMENT&category=1&apiClassify=3&interName=%E9%A1%BA%E4%B8%B0%E6%9C%8D%E5%8A%A1%E7%BD%91%E6%9F%A5%E8%AF%A2%E6%8E%A5%E5%8F%A3-EXP_RECE_QUERY_GIS_DEPARTMENT)
- **用途**：按地址/坐标查询周边顺丰服务网点。

**官方请求示例（msgData）：** `address`, `x`, `y`, `opt`(如 dq0), `deptType`(如 1|2|5), `servType`(如 1|2|5), `distance`(米)。

**本系统调用方式：**

- `GET /api/sf/outlets?address=广东省深圳市宝安区劳动路&language=0`
- 可选：`x`, `y`, `opt`, `deptType`, `servType`, `distance`  
  返回顺丰原始 `apiResultCode`、`apiErrorMsg`、`msgData`（网点列表等）。

---

## 三、代码位置

| 说明           | 路径 |
|----------------|------|
| 配置类         | `com.oms.config.SfExpressConfig` |
| 顺丰 API 服务  | `com.oms.service.SfExpressApiService` |
| 顺丰 Controller | `com.oms.controller.SfExpressController` |
| 统一物流查询   | `LogisticsService` 内顺丰分支委托 `SfExpressApiService.searchRoutes` |

---

## 四、签名规则

与顺丰开放平台一致：

1. 拼接待签串：`msgData + timestamp + checkword`（UTF-8）。
2. 对上述字符串做 **MD5**，取 32 位**大写十六进制**作为 `msgDigest`。

请求体示例：

```json
{
  "partnerID": "FCKZH1AEF3YE",
  "requestID": "uuid无横线",
  "serviceCode": "EXP_RECE_SEARCH_ROUTES",
  "timestamp": 1739012345678,
  "msgData": "{\"language\":\"zh-CN\",\"trackingType\":\"1\",\"trackingNumber\":[\"SF5122068084664\"],\"methodType\":\"1\",\"checkPhoneNo\":\"8805\"}",
  "msgDigest": "MD5_HEX_UPPERCASE"
}
```

---

## 五、平台/业务错误码（参考）

| 平台码 | 说明 |
|--------|------|
| A1000 | 平台校验成功，需再看 apiResultData 内业务结果 |
| A1001 | 必传参数不可为空：检查必填、Content-Type、URL 编码、msgData 为 JSON、整体为 form |
| A1003 | IP 无效 |
| A1004 | 无对应服务权限（环境/关联接口配置） |
| A1005 | 流量受控（如 30 次/秒、3000 次/天） |
| A1006 | 数字签名无效（checkword/加签/特殊字符） |

业务码示例：6133（method_type 传 1 或 2）、8013（未传 tracking_number）、8003/8004（单号超 10 个）；网点接口 S0000=成功、S0001=非法 JSON 等。

按上述配置与接口使用即可一次配通顺丰路由查询与网点查询；若返回 A1001，重点检查必填与 Content-Type/form 要求。
