package com.oms.service;

import com.oms.entity.SalesOrder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class SalesOrderReceiptFlowHelper {
    public static final String FILTER_PENDING_UPLOAD = "pending_upload";
    public static final String FILTER_WAITING_MOTHER = "waiting_mother";
    public static final String FILTER_MOTHER_DELIVERED = "mother_delivered";
    public static final String FILTER_RETURN_DELIVERED = "return_delivered";
    public static final String FILTER_WAITING_SIGN = "waiting_sign";
    public static final String FILTER_SELF_VEHICLE_PENDING_RECEIPT = "self_vehicle_pending_receipt";
    public static final String FILTER_COMPLETED = "completed";

    public static final String STAGE_PENDING_MOTHER = "PENDING_MOTHER";
    public static final String STAGE_MOTHER_DELIVERED = "MOTHER_DELIVERED";
    public static final String STAGE_RETURN_DELIVERED = "RETURN_DELIVERED";
    public static final String STAGE_SELF_VEHICLE_PENDING_RECEIPT = "SELF_VEHICLE_PENDING_RECEIPT";
    public static final String STAGE_FLOW_COMPLETED = "FLOW_COMPLETED";

    public static final String STATUS_UPLOADED = "已上传";
    public static final String STATUS_COMPLETED = "妥投结束";
    public static final String DELIVERY_METHOD_SELF_VEHICLE = "自主车辆配送";

    private SalesOrderReceiptFlowHelper() {
    }

    public static boolean isSelfVehicleDelivery(String deliveryMethod) {
        return DELIVERY_METHOD_SELF_VEHICLE.equals(trimToEmpty(deliveryMethod));
    }

    public static boolean needReceiptSlip(SalesOrder order) {
        return order != null && Boolean.TRUE.equals(order.getNeedReceiptSlip());
    }

    public static boolean isShippedStatus(String status) {
        return "已发货".equals(trimToEmpty(status));
    }

    public static boolean hasUploadedReceipt(SalesOrder order) {
        if (order == null) return false;
        String receiptUrl = trimToEmpty(order.getReceiptUrl());
        if (receiptUrl.isEmpty()) return false;
        String receiptStatus = trimToEmpty(order.getReceiptStatus());
        return STATUS_UPLOADED.equals(receiptStatus) || STATUS_COMPLETED.equals(receiptStatus);
    }

    public static String normalizeStage(String stage) {
        String normalized = trimToEmpty(stage);
        return normalized.isEmpty() ? null : normalized;
    }

    public static int stageRank(String stage) {
        String normalized = normalizeStage(stage);
        if (normalized == null) return 0;
        switch (normalized) {
            case STAGE_PENDING_MOTHER:
            case STAGE_SELF_VEHICLE_PENDING_RECEIPT:
                return 1;
            case STAGE_MOTHER_DELIVERED:
                return 2;
            case STAGE_RETURN_DELIVERED:
                return 3;
            case STAGE_FLOW_COMPLETED:
                return 4;
            default:
                return 0;
        }
    }

    public static String advanceStage(String currentStage, String targetStage) {
        String current = normalizeStage(currentStage);
        String target = normalizeStage(targetStage);
        if (target == null) return current;
        if (current == null || stageRank(target) >= stageRank(current)) {
            return target;
        }
        return current;
    }

    public static String determineReceiptFlowStage(SalesOrder order) {
        if (order == null) return null;
        String currentStage = normalizeStage(order.getReceiptFlowStage());
        if (hasUploadedReceipt(order)) {
            return advanceStage(currentStage, STAGE_FLOW_COMPLETED);
        }
        if (isSelfVehicleDelivery(order.getDeliveryMethod()) && isShippedStatus(order.getStatus())) {
            return advanceStage(currentStage, STAGE_SELF_VEHICLE_PENDING_RECEIPT);
        }
        if (needReceiptSlip(order) && isShippedStatus(order.getStatus())) {
            if (currentStage == null) {
                return STAGE_PENDING_MOTHER;
            }
            return currentStage;
        }
        return currentStage;
    }

    public static Specification<SalesOrder> receiptFilterSpec(String filter) {
        String normalized = trimToEmpty(filter).toLowerCase();
        if (normalized.isEmpty() || "all".equals(normalized)) {
            return null;
        }
        switch (normalized) {
            case FILTER_PENDING_UPLOAD:
                return shippedSpec().and(nonSelfVehicleSpec()).and(needReceiptSlipSpec()).and(emptyReceiptUrlSpec());
            case FILTER_WAITING_MOTHER:
                return shippedSpec().and(nonSelfVehicleSpec()).and(needReceiptSlipSpec()).and(emptyReceiptUrlSpec())
                        .and(stageEqualsAnySpec(null, "", STAGE_PENDING_MOTHER));
            case FILTER_MOTHER_DELIVERED:
                return shippedSpec().and(nonSelfVehicleSpec()).and(needReceiptSlipSpec()).and(emptyReceiptUrlSpec())
                        .and(stageEqualsAnySpec(STAGE_MOTHER_DELIVERED));
            case FILTER_RETURN_DELIVERED:
                return shippedSpec().and(nonSelfVehicleSpec()).and(needReceiptSlipSpec()).and(emptyReceiptUrlSpec())
                        .and(stageEqualsAnySpec(STAGE_RETURN_DELIVERED));
            case FILTER_WAITING_SIGN:
                return shippedSpec().and(nonSelfVehicleSpec()).and(notNeedReceiptSlipSpec()).and(emptyReceiptUrlSpec()).and(notFlowCompletedSpec());
            case FILTER_SELF_VEHICLE_PENDING_RECEIPT:
                return shippedSpec().and(selfVehicleSpec()).and(emptyReceiptUrlSpec());
            case FILTER_COMPLETED:
                return shippedSpec().and(completedSpec());
            default:
                return null;
        }
    }

    public static Specification<SalesOrder> shippedSpec() {
        return (root, query, cb) -> cb.equal(root.get("status"), "已发货");
    }

    public static Specification<SalesOrder> completedSpec() {
        return (root, query, cb) -> {
            Predicate uploaded = uploadedReceiptPredicate(root, cb);
            Predicate selfVehicle = selfVehiclePredicate(root, cb);
            Predicate noNeedReceiptCompleted = cb.and(
                    cb.or(cb.isNull(root.get("needReceiptSlip")), cb.equal(root.get("needReceiptSlip"), false)),
                    cb.equal(root.get("receiptFlowStage"), STAGE_FLOW_COMPLETED),
                    cb.or(cb.isNull(root.get("deliveryMethod")), cb.notEqual(root.get("deliveryMethod"), DELIVERY_METHOD_SELF_VEHICLE))
            );
            return cb.or(
                    uploaded,
                    cb.and(selfVehicle, uploaded),
                    noNeedReceiptCompleted
            );
        };
    }

    public static Specification<SalesOrder> uploadedReceiptSpec() {
        return (root, query, cb) -> uploadedReceiptPredicate(root, cb);
    }

    public static Specification<SalesOrder> emptyReceiptUrlSpec() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("receiptUrl")),
                cb.equal(root.get("receiptUrl"), "")
        );
    }

    public static Specification<SalesOrder> selfVehicleSpec() {
        return (root, query, cb) -> selfVehiclePredicate(root, cb);
    }

    public static Specification<SalesOrder> nonSelfVehicleSpec() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("deliveryMethod")),
                cb.equal(root.get("deliveryMethod"), ""),
                cb.notEqual(root.get("deliveryMethod"), DELIVERY_METHOD_SELF_VEHICLE)
        );
    }

    public static Specification<SalesOrder> needReceiptSlipSpec() {
        return (root, query, cb) -> cb.equal(root.get("needReceiptSlip"), true);
    }

    public static Specification<SalesOrder> notNeedReceiptSlipSpec() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("needReceiptSlip")),
                cb.equal(root.get("needReceiptSlip"), false)
        );
    }

    public static Specification<SalesOrder> stageEqualsAnySpec(String... stages) {
        return (root, query, cb) -> {
            Predicate[] predicates = new Predicate[stages.length];
            for (int i = 0; i < stages.length; i++) {
                String stage = stages[i];
                if (stage == null) {
                    predicates[i] = cb.isNull(root.get("receiptFlowStage"));
                } else {
                    predicates[i] = cb.equal(root.get("receiptFlowStage"), stage);
                }
            }
            return cb.or(predicates);
        };
    }

    public static Specification<SalesOrder> notFlowCompletedSpec() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("receiptFlowStage")),
                cb.equal(root.get("receiptFlowStage"), ""),
                cb.notEqual(root.get("receiptFlowStage"), STAGE_FLOW_COMPLETED)
        );
    }

    public static Specification<SalesOrder> notSpec(Specification<SalesOrder> spec) {
        return (root, query, cb) -> cb.not(spec.toPredicate(root, query, cb));
    }

    private static Predicate uploadedReceiptPredicate(jakarta.persistence.criteria.Root<SalesOrder> root,
                                                      jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.and(
                cb.isNotNull(root.get("receiptUrl")),
                cb.notEqual(root.get("receiptUrl"), ""),
                root.get("receiptStatus").in(STATUS_UPLOADED, STATUS_COMPLETED)
        );
    }

    private static Predicate selfVehiclePredicate(jakarta.persistence.criteria.Root<SalesOrder> root,
                                                  jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.equal(root.get("deliveryMethod"), DELIVERY_METHOD_SELF_VEHICLE);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
