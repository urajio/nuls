/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.consensus.poc.service.impl.tx;

import io.nuls.consensus.poc.protocol.constant.ConsensusStatusEnum;
import io.nuls.consensus.poc.protocol.event.notice.RegisterAgentNotice;
import io.nuls.consensus.poc.protocol.model.Agent;
import io.nuls.consensus.poc.protocol.tx.RegisterAgentTransaction;
import io.nuls.consensus.poc.protocol.utils.ConsensusTool;
import io.nuls.db.dao.AgentDataService;
import io.nuls.db.dao.DepositDataService;
import io.nuls.db.entity.AgentPo;
import io.nuls.db.entity.DepositPo;
import io.nuls.db.transactional.annotation.DbSession;
import io.nuls.db.transactional.annotation.PROPAGATION;
import io.nuls.event.bus.service.intf.EventBroadcaster;
import io.nuls.protocol.context.NulsContext;
import io.nuls.protocol.event.entity.Consensus;
import io.nuls.protocol.model.Block;
import io.nuls.protocol.service.intf.TransactionService;

/**
 * @author Niels
 * @date 2018/1/8
 */
@DbSession(transactional = PROPAGATION.NONE)
public class RegisterAgentTxService implements TransactionService<RegisterAgentTransaction> {
    private AgentDataService agentDataService = NulsContext.getServiceBean(AgentDataService.class);
    private DepositDataService depositDataService = NulsContext.getServiceBean(DepositDataService.class);

    @Override
    @DbSession
    public void onRollback(RegisterAgentTransaction tx, Block block) {
//        this.agentDataService.deleteById(tx.getTxData().getHexHash(), tx.getBlockHeight());

        agentDataService.realDeleteById(tx.getTxData().getHexHash(), 0);

        DepositPo delPo = new DepositPo();
        delPo.setAgentHash(tx.getTxData().getHexHash());
        delPo.setDelHeight(tx.getBlockHeight());
        this.depositDataService.realDeleteByAgentHash(delPo);
    }

    @Override
    @DbSession
    public void onCommit(RegisterAgentTransaction tx, Block block) {
        Consensus<Agent> ca = tx.getTxData();
        ca.getExtend().setBlockHeight(tx.getBlockHeight());
        ca.getExtend().setStatus(ConsensusStatusEnum.WAITING.getCode());

        AgentPo po = ConsensusTool.agentToPojo(ca);
        agentDataService.save(po);

        RegisterAgentNotice notice = new RegisterAgentNotice();
        notice.setEventBody(tx);
        NulsContext.getServiceBean(EventBroadcaster.class).publishToLocal(notice);
    }


    @Override
    public void onApproval(RegisterAgentTransaction tx, Block block) {
    }
}
