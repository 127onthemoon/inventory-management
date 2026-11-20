package com.cumt.inventory.controller.api;

import com.cumt.inventory.common.ApiResponse;
import com.cumt.inventory.dto.ConsistencyReport;
import com.cumt.inventory.dto.StockInRequest;
import com.cumt.inventory.dto.StockOperationResult;
import com.cumt.inventory.dto.StockOutRequest;
import com.cumt.inventory.service.ProductService;
import com.cumt.inventory.service.StatisticsService;
import com.cumt.inventory.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 出入库 REST 接口（压测与演示使用）。
 * <ul>
 *   <li>200 + code=OK：本次操作成功生效</li>
 *   <li>200 + code=DUPLICATE：重复提交，未重复变更库存，返回首次结果</li>
 *   <li>409 + code=INSUFFICIENT_STOCK：库存不足，业务拒绝（不是系统错误）</li>
 *   <li>400：参数错误；404：商品不存在；503：并发冲突重试仍失败；500：系统错误</li>
 * </ul>
 * 幂等号可放在请求体 requestId 字段，或请求头 Idempotency-Key 中。
 */
@RestController
@RequestMapping("/api/stock")
public class StockApiController {

    private final StockService stockService;
    private final StatisticsService statisticsService;
    private final ProductService productService;

    public StockApiController(StockService stockService, StatisticsService statisticsService,
                              ProductService productService) {
        this.stockService = stockService;
        this.statisticsService = statisticsService;
        this.productService = productService;
    }

    @PostMapping("/out")
    public ResponseEntity<ApiResponse<StockOperationResult>> out(
            @Valid @RequestBody StockOutRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            Authentication auth) {
        if (req.getRequestId() == null && idemKey != null) {
            req.setRequestId(idemKey);
        }
        return respond(stockService.stockOut(req, auth.getName()));
    }

    @PostMapping("/in")
    public ResponseEntity<ApiResponse<StockOperationResult>> in(
            @Valid @RequestBody StockInRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            Authentication auth) {
        if (req.getRequestId() == null && idemKey != null) {
            req.setRequestId(idemKey);
        }
        return respond(stockService.stockIn(req, auth.getName()));
    }

    /** 一致性核对：不传 productId 为全量核对 */
    @GetMapping("/consistency")
    public ApiResponse<?> consistency(@RequestParam(required = false) Long productId) {
        if (productId != null) {
            ConsistencyReport.Item item = statisticsService.check(productService.get(productId));
            return ApiResponse.ok(item);
        }
        return ApiResponse.ok(statisticsService.consistency());
    }

    private static ResponseEntity<ApiResponse<StockOperationResult>> respond(StockOperationResult r) {
        return r.duplicate()
                ? ResponseEntity.ok(ApiResponse.ok("DUPLICATE", "重复提交，已返回首次处理结果", r))
                : ResponseEntity.ok(ApiResponse.ok(r));
    }
}
