/**
 * @Title XN623070.java 
 * @Package com.cdkj.ylq.api.impl 
 * @Description 
 * @author leo(haiqing)  
 * @date 2017年8月16日 下午4:31:31 
 * @version V1.0   
 */
package com.cdkj.ylq.api.impl;

import com.cdkj.ylq.ao.IBorrowAO;
import com.cdkj.ylq.api.AProcessor;
import com.cdkj.ylq.common.JsonUtil;
import com.cdkj.ylq.core.StringValidater;
import com.cdkj.ylq.dto.req.XN623071Req;
import com.cdkj.ylq.dto.res.BooleanRes;
import com.cdkj.ylq.exception.BizException;
import com.cdkj.ylq.exception.ParaException;
import com.cdkj.ylq.spring.SpringContextHolder;

/** 
 * 打款回录
 * @author: haiqingzheng 
 * @since: 2017年8月16日 下午4:31:31 
 * @history:
 */
public class XN623071 extends AProcessor {

    private IBorrowAO borrowAO = SpringContextHolder.getBean(IBorrowAO.class);

    private XN623071Req req = null;

    /** 
     * @see com.cdkj.ylq.api.IProcessor#doBusiness()
     */
    @Override
    public Object doBusiness() throws BizException {
        borrowAO.doLoanOffline(req.getCode(), req.getResult(), req.getUpdater(),
            req.getRemark());
        return new BooleanRes(true);
    }

    /** 
     * @see com.cdkj.ylq.api.IProcessor#doCheck(java.lang.String)
     */
    @Override
    public void doCheck(String inputparams) throws ParaException {
        req = JsonUtil.json2Bean(inputparams, XN623071Req.class);
        StringValidater.validateBlank(req.getCode(), req.getResult(),
            req.getUpdater());
    }

}
