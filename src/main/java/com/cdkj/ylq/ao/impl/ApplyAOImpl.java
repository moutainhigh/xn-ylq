package com.cdkj.ylq.ao.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdkj.ylq.ao.IApplyAO;
import com.cdkj.ylq.bo.IApplyBO;
import com.cdkj.ylq.bo.ICertificationBO;
import com.cdkj.ylq.bo.IProductBO;
import com.cdkj.ylq.bo.IUserBO;
import com.cdkj.ylq.bo.base.Paginable;
import com.cdkj.ylq.common.DateUtil;
import com.cdkj.ylq.common.JsonUtil;
import com.cdkj.ylq.core.OrderNoGenerater;
import com.cdkj.ylq.domain.Apply;
import com.cdkj.ylq.domain.Certification;
import com.cdkj.ylq.domain.InfoAmount;
import com.cdkj.ylq.enums.EApplyStatus;
import com.cdkj.ylq.enums.EBoolean;
import com.cdkj.ylq.enums.ECertiKey;
import com.cdkj.ylq.enums.EGeneratePrefix;
import com.cdkj.ylq.exception.BizException;

@Service
public class ApplyAOImpl implements IApplyAO {

    @Autowired
    private IApplyBO applyBO;

    @Autowired
    private IUserBO userBO;

    @Autowired
    private IProductBO productBO;

    @Autowired
    private ICertificationBO certificationBO;

    @Override
    public String submitApply(String applyUser, String productCode) {
        userBO.getRemoteUser(applyUser);
        Apply apply = applyBO.getCurrentApply(applyUser, productCode);
        if (apply != null) {
            throw new BizException("xn623020", "您已经有贷款申请");
        }
        Apply data = new Apply();
        String code = OrderNoGenerater.generateM(EGeneratePrefix.APPLY
            .getCode());
        data.setCode(code);
        data.setApplyUser(applyUser);
        data.setApplyDatetime(new Date());
        data.setProductCode(productCode);
        data.setStatus(EApplyStatus.TO_CERTI.getCode());
        data.setUpdater(applyUser);
        data.setUpdateDatetime(new Date());
        data.setRemark("新申请");
        applyBO.saveApply(data);
        return code;
    }

    @Override
    public void cancalApply(String applyUser, String productCode) {
        Apply apply = applyBO.getCurrentApply(applyUser, productCode);
        if (!EApplyStatus.TO_CERTI.getCode().equals(apply.getStatus())
                && !EApplyStatus.TO_APPROVE.getCode().equals(apply.getStatus())
                && !EApplyStatus.APPROVE_NO.getCode().equals(apply.getStatus())
                && !EApplyStatus.APPROVE_YES.getCode()
                    .equals(apply.getStatus())) {
            throw new BizException("xn623021", "当前状态不能取消");
        }
        applyBO.cancel(apply);

    }

    @Override
    @Transactional
    public void doApprove(String code, String approveResult, Long sxAmount,
            String approver, String approveNote) {
        Apply apply = applyBO.getApply(code);
        if (!EApplyStatus.TO_APPROVE.getCode().equals(apply.getStatus())) {
            throw new BizException("xn623021", "该申请记录不处于待审核状态");
        }
        String status = EApplyStatus.APPROVE_NO.getCode();
        if (EBoolean.YES.getCode().equals(approveResult)) {
            status = EApplyStatus.APPROVE_YES.getCode();
            // 落地授信信息
            InfoAmount infoAmount = new InfoAmount();
            infoAmount.setSxAmount(sxAmount);
            Certification certification = certificationBO.getCertification(
                apply.getApplyUser(), ECertiKey.INFO_AMOUNT.getCode());
            if (certification != null) {
                certification.setFlag(EBoolean.YES.getCode());
                certification.setResult(JsonUtil.Object2Json(infoAmount));
                certification.setCerDatetime(new Date());
                certification.setValidDatetime(DateUtil.getRelativeDateOfDays(
                    DateUtil.getTodayStart(), 7));
                certification.setRef(apply.getCode());
                certificationBO.refreshCertification(certification);
            } else {
                certification = new Certification();
                certification.setUserId(apply.getApplyUser());
                certification.setCertiKey(ECertiKey.INFO_AMOUNT.getCode());
                certification.setFlag(EBoolean.YES.getCode());
                certification.setResult(JsonUtil.Object2Json(sxAmount));
                certification.setCerDatetime(new Date());
                certification.setValidDatetime(DateUtil.getRelativeDateOfDays(
                    DateUtil.getTodayStart(), 7));
                certification.setRef(apply.getCode());
                certificationBO.saveCertification(certification);
            }
        } else {
            sxAmount = 0L;
        }
        applyBO.doApprove(apply, status, sxAmount, approver, approveNote);
    }

    @Override
    public Paginable<Apply> queryApplyPage(int start, int limit, Apply condition) {
        Paginable<Apply> results = applyBO
            .getPaginable(start, limit, condition);
        List<Apply> applyList = results.getList();
        for (Apply apply : applyList) {
            apply.setUser(userBO.getRemoteUser(apply.getApplyUser()));
            apply.setProduct(productBO.getProduct(apply.getProductCode()));
        }
        return results;
    }

    @Override
    public Apply getApply(String code) {
        Apply apply = applyBO.getApply(code);
        apply.setUser(userBO.getRemoteUser(apply.getApplyUser()));
        apply.setProduct(productBO.getProduct(apply.getProductCode()));
        return apply;
    }

}