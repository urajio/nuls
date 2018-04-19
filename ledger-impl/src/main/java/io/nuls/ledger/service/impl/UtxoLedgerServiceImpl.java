/**
 * MIT License
 * *
 * Copyright (c) 2017-2018 nuls.io
 * *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.ledger.service.impl;

import io.nuls.core.constant.ErrorCode;
import io.nuls.core.constant.NulsConstant;
import io.nuls.core.dto.Page;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.model.Result;
import io.nuls.core.utils.date.TimeService;
import io.nuls.core.utils.log.BlockLog;
import io.nuls.core.utils.log.Log;
import io.nuls.core.utils.param.AssertUtil;
import io.nuls.core.utils.spring.lite.annotation.Autowired;
import io.nuls.core.utils.str.StringUtils;
import io.nuls.core.validate.ValidateResult;
import io.nuls.db.dao.UtxoOutputDataService;
import io.nuls.db.dao.UtxoTransactionDataService;
import io.nuls.db.entity.TransactionLocalPo;
import io.nuls.db.entity.TransactionPo;
import io.nuls.db.entity.UtxoInputPo;
import io.nuls.db.entity.UtxoOutputPo;
import io.nuls.db.transactional.annotation.DbSession;
import io.nuls.event.bus.service.intf.EventBroadcaster;
import io.nuls.ledger.constant.LedgerConstant;
import io.nuls.ledger.entity.Balance;
import io.nuls.ledger.entity.OutPutStatusEnum;
import io.nuls.ledger.entity.UtxoData;
import io.nuls.ledger.entity.UtxoOutput;
import io.nuls.ledger.entity.params.Coin;
import io.nuls.ledger.entity.params.CoinTransferData;
import io.nuls.ledger.entity.params.OperationType;
import io.nuls.ledger.entity.tx.AbstractCoinTransaction;
import io.nuls.ledger.entity.tx.LockNulsTransaction;
import io.nuls.ledger.entity.tx.TransferTransaction;
import io.nuls.ledger.service.intf.LedgerService;
import io.nuls.ledger.util.UtxoTransactionTool;
import io.nuls.ledger.util.UtxoTransferTool;
import io.nuls.protocol.constant.TransactionConstant;
import io.nuls.protocol.constant.TxStatusEnum;
import io.nuls.protocol.context.NulsContext;
import io.nuls.protocol.event.TransactionEvent;
import io.nuls.protocol.model.Block;
import io.nuls.protocol.model.Na;
import io.nuls.protocol.model.NulsDigestData;
import io.nuls.protocol.model.Transaction;
import io.nuls.protocol.service.intf.TransactionService;
import io.nuls.protocol.utils.TransactionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Niels
 * @date 2017/11/13
 */
public class UtxoLedgerServiceImpl implements LedgerService {

    @Autowired
    private UtxoTransactionDataService txDao;
    @Autowired
    private UtxoOutputDataService outputDataService;
    @Autowired
    private EventBroadcaster eventBroadcaster;

    private Lock lock = new ReentrantLock();

    @Override
    public Transaction getTx(NulsDigestData hash) {
        TransactionPo po = txDao.gettx(hash.getDigestHex());
        if (null == po) {
            return null;
        }
        try {
            return UtxoTransferTool.toTransaction(po);
        } catch (Exception e) {
            Log.error(e);
        }
        return null;
    }

    @Override
    public Transaction getTx(String fromHash, int fromIndex) {
        UtxoInputPo inputPo = txDao.getTxInput(fromHash, fromIndex);
        if (inputPo == null) {
            return null;
        }
        TransactionPo po = txDao.gettx(inputPo.getTxHash());
        try {
            return UtxoTransferTool.toTransaction(po);
        } catch (Exception e) {
            Log.error(e);
        }
        return null;
    }

    @Override
    public List<Transaction> getTxList(String address, int txType) throws Exception {
        if (StringUtils.isBlank(address) && txType == 0) {
            throw new NulsRuntimeException(ErrorCode.PARAMETER_ERROR);
        }
        List<Transaction> txList = new ArrayList<>();

        if (StringUtils.isNotBlank(address) && NulsContext.LOCAL_ADDRESS_LIST.contains(address)) {
            List<TransactionLocalPo> poList = txDao.getLocalTxs(null, address, txType, 0, 0);
            for (TransactionLocalPo po : poList) {
                txList.add(UtxoTransferTool.toTransaction(po));
            }
        } else {
            List<TransactionPo> poList = txDao.getTxs(null, address, txType, 0, 0);
            for (TransactionPo po : poList) {
                txList.add(UtxoTransferTool.toTransaction(po));
            }
        }
        return txList;
    }

    @Override
    public long getTxCount(Long blockHeight, String address, int txType) throws Exception {
        long count = 0;
        boolean isLocal = NulsContext.LOCAL_ADDRESS_LIST.contains(address);
        if (isLocal) {
            count = txDao.getLocalTxsCount(blockHeight, address, txType);
        } else {
            count = txDao.getTxsCount(blockHeight, address, txType);
        }
        return count;
    }

    @Override
    public List<Transaction> getTxList(Long blockHeight, String address, int txType, int pageNumber, int pageSize) throws Exception {
        List<Transaction> txList = null;
        if (StringUtils.isNotBlank(address) && NulsContext.LOCAL_ADDRESS_LIST.contains(address)) {
            txList = getLocalTxList(blockHeight, address, txType, pageNumber, pageSize);
        } else {
            txList = new ArrayList<>();
            List<TransactionPo> poList;
            poList = txDao.getTxs(blockHeight, address, txType, pageNumber, pageSize);
            for (TransactionPo po : poList) {
                txList.add(UtxoTransferTool.toTransaction(po));
            }
        }
        return txList;
    }

    public List<Transaction> getLocalTxList(Long blockHeight, String address, int txType, int pageNumber, int pageSize) throws Exception {
        List<Transaction> txList = new ArrayList<>();
        List<TransactionLocalPo> poList = txDao.getLocalTxs(blockHeight, address, txType, pageNumber, pageSize);
        for (TransactionLocalPo po : poList) {
            txList.add(UtxoTransferTool.toTransaction(po));
        }
        return txList;
    }

    @Override
    public List<Transaction> getTxList(String blockHash) throws Exception {
        List<Transaction> txList = new ArrayList<>();
        List<TransactionPo> poList = txDao.getTxs(blockHash);
        if (null == poList) {
            return txList;
        }
        for (TransactionPo po : poList) {
            txList.add(UtxoTransferTool.toTransaction(po));
        }
        return txList;
    }

    @Override
    public List<Transaction> getTxList(long startHeight, long endHeight) throws Exception {
        List<Transaction> txList = new ArrayList<>();
        List<TransactionPo> poList = txDao.getTxs(startHeight, endHeight);
        for (TransactionPo po : poList) {
            txList.add(UtxoTransferTool.toTransaction(po));
        }
        return txList;
    }

    @Override
    public List<Transaction> getTxList(long height) throws Exception {
        List<Transaction> txList = new ArrayList<>();
        List<TransactionPo> poList = txDao.getTxs(height);
        for (TransactionPo po : poList) {
            txList.add(UtxoTransferTool.toTransaction(po));
        }
        return txList;
    }

    @Override
    public Page<Transaction> getTxList(Long height, int type, int pageNum, int pageSize) throws Exception {
        Page<TransactionPo> poPage = txDao.getTxs(height, type, pageNum, pageSize);
        Page<Transaction> txPage = new Page<>(poPage);
        List<Transaction> txList = new ArrayList<>();
        for (TransactionPo po : poPage.getList()) {
            txList.add(UtxoTransferTool.toTransaction(po));
        }
        txPage.setList(txList);
        return txPage;
    }

    @Override
    public Balance getBalance(String address) {
        List<UtxoOutputPo> poList = outputDataService.getAccountUnSpend(address);
        Balance balance = new Balance();
        long usable = 0;
        long locked = 0;

        for (UtxoOutputPo po : poList) {
            if (po.isLocked()) {
                locked += po.getValue();
            } else {
                usable += po.getValue();
            }
        }

        balance.setUsable(Na.valueOf(usable));
        balance.setLocked(Na.valueOf(locked));
        balance.setBalance(Na.valueOf(usable + locked));
        return balance;
    }

//    public Balance getBalance(String address) {
//        if (StringUtils.isNotBlank(address)) {
//            Balance balance = ledgerCacheService.getBalance(address);
//            return balance;
//        } else {
//            Balance allBalance = new Balance();
//            long usable = 0;
//            long locked = 0;
//            for (String addr : NulsContext.LOCAL_ADDRESS_LIST) {
//                Balance balance = ledgerCacheService.getBalance(addr);
//                if (null != balance) {
//                    usable += balance.getUsable().getValue();
//                    locked += balance.getLocked().getValue();
//                }
//            }
//            allBalance.setUsable(Na.valueOf(usable));
//            allBalance.setLocked(Na.valueOf(locked));
//            allBalance.setBalance(Na.valueOf(usable + locked));
//            return allBalance;
//        }
//    }

    @Override
    public Na getTxFee(int txType) {
        Block bestBlock = NulsContext.getInstance().getBestBlock();
        if (null == bestBlock) {
            return LedgerConstant.TRANSACTION_FEE;
        }
        long blockHeight = bestBlock.getHeader().getHeight();
        if (txType == TransactionConstant.TX_TYPE_COIN_BASE ||
                txType == TransactionConstant.TX_TYPE_SMALL_CHANGE
                || txType == TransactionConstant.TX_TYPE_CANCEL_DEPOSIT
                ) {
            return Na.ZERO;
        }
        long x = blockHeight / LedgerConstant.BLOCK_COUNT_OF_YEAR + 1;
        return LedgerConstant.TRANSACTION_FEE.div(x);
    }

    @Override
    public Result transfer(String address, String password, String toAddress, Na amount, String remark) {
        CoinTransferData coinData = new CoinTransferData(OperationType.TRANSFER, amount, address, toAddress, getTxFee(TransactionConstant.TX_TYPE_TRANSFER));
        return transfer(coinData, password, remark);
    }

    private Result transfer(CoinTransferData coinData, String password, String remark) {
        TransferTransaction tx = null;
        try {
            tx = UtxoTransactionTool.getInstance().createTransferTx(coinData, password, remark);
            ValidateResult result = tx.verify();
            if (result.isFailed()) {
                throw new NulsException(result.getErrorCode());
            }
            TransactionEvent event = new TransactionEvent();
            event.setEventBody(tx);
            eventBroadcaster.publishToLocal(event);
        } catch (Exception e) {
            Log.error(e);
            return new Result(false, e.getMessage());
        }
        return new Result(true, "OK", tx.getHash().getDigestHex());
    }

    @Override
    public Result transfer(List<String> addressList, String password, String toAddress, Na amount, String remark) {
        CoinTransferData coinData = new CoinTransferData(OperationType.TRANSFER, amount, addressList, toAddress, getTxFee(TransactionConstant.TX_TYPE_TRANSFER));
        return transfer(coinData, password, remark);
    }

    @Override
    public Result lock(String address, String password, Na amount, long unlockTime, String remark) {
        LockNulsTransaction tx = null;
        try {
            CoinTransferData coinData = new CoinTransferData(OperationType.LOCK, amount, address, getTxFee(TransactionConstant.TX_TYPE_LOCK));
            coinData.addTo(address, new Coin(amount, unlockTime));
            tx = UtxoTransactionTool.getInstance().createLockNulsTx(coinData, password, remark);

            tx.verify();
            TransactionEvent event = new TransactionEvent();
            event.setEventBody(tx);
            eventBroadcaster.publishToLocal(event);

        } catch (Exception e) {
            Log.error(e);
            try {
                rollbackTx(tx, null);
            } catch (NulsException e1) {
                Log.error(e1);
            }
            return new Result(false, e.getMessage());
        }

        return new Result(true, "OK", tx.getHash().getDigestHex());
    }

    @Override
    @DbSession
    public void saveTxList(List<Transaction> txList) throws IOException {
        lock.lock();
        try {
            boolean isMine;
            List<TransactionPo> poList = new ArrayList<>();
            List<TransactionLocalPo> localPoList = new ArrayList<>();
            for (int i = 0; i < txList.size(); i++) {
                Transaction tx = txList.get(i);
                TransactionPo po = UtxoTransferTool.toTransactionPojo(tx);

                poList.add(po);
                if (tx.getType() == TransactionConstant.TX_TYPE_CANCEL_DEPOSIT) {
                    isMine = tx.isMine();
                } else {
                    isMine = this.checkTxIsMine(tx);
                }
                if (isMine) {
                    TransactionLocalPo localPo = UtxoTransferTool.toLocalTransactionPojo(tx);
                    localPoList.add(localPo);
                }
            }

            txDao.saveTxList(poList);
            if (localPoList.size() > 0) {
                txDao.saveLocalList(localPoList);
            }
        } catch (Exception e) {
            Log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean saveLocalTx(Transaction tx) throws IOException {
        TransactionLocalPo localPo = UtxoTransferTool.toLocalTransactionPojo(tx);
        txDao.save(localPo);
        return false;
    }

    @Override
    @DbSession
    public void saveTxInLocal(String address) {
        List<TransactionPo> poList = txDao.getTxs(null, address, 0, 0, 0);
        if (poList.isEmpty()) {
            return;
        }
        List<TransactionLocalPo> localPoList = new ArrayList<>();

        for (TransactionPo po : poList) {
            TransactionLocalPo localPo = txDao.getLocaltx(po.getHash());
            if (localPo != null) {
                continue;
            }

            localPo = new TransactionLocalPo(po, Transaction.TRANSFER_RECEIVE);
            for (UtxoInputPo inputPo : po.getInputs()) {
                if (inputPo.getFromOutPut().getAddress().equals(address)) {
                    localPo.setTransferType(Transaction.TRANSFER_SEND);
                    break;
                }
            }
            localPoList.add(localPo);
        }
        txDao.saveLocalList(localPoList);
    }


    @Override
    public boolean checkTxIsMine(Transaction tx) {
        if (tx instanceof AbstractCoinTransaction) {
            return UtxoTransactionTool.getInstance().isMine((AbstractCoinTransaction) tx);
        }
        return false;
    }

    @Override
    public boolean checkTxIsMine(Transaction tx, String address) {
        if (tx instanceof AbstractCoinTransaction) {
            return UtxoTransactionTool.getInstance().isMine((AbstractCoinTransaction) tx, address);
        }
        return false;
    }

    @Override
    public boolean checkTxIsMySend(Transaction tx) {
        if (tx instanceof AbstractCoinTransaction) {
            return UtxoTransactionTool.getInstance().isMySend((AbstractCoinTransaction) tx);
        }
        return false;
    }

    @Override
    public boolean checkTxIsMySend(Transaction tx, String address) {
        if (tx instanceof AbstractCoinTransaction) {
            return UtxoTransactionTool.getInstance().isMySend((AbstractCoinTransaction) tx, address);
        }
        return false;
    }

    @Override

    public void rollbackTx(Transaction tx, Block block) throws NulsException {
        AssertUtil.canNotEmpty(tx, ErrorCode.NULL_PARAMETER);
        if (tx.getStatus() == TxStatusEnum.CACHED) {
            return;
        }
        BlockLog.debug("rollback tx ==================================================", tx.getHash());
        List<TransactionService> serviceList = getServiceList(tx.getClass());
        for (TransactionService service : serviceList) {
            service.onRollback(tx, block);
        }
        tx.setStatus(TxStatusEnum.CACHED);
    }

    @Override
    @DbSession
    public void commitTx(Transaction tx, Block block) throws NulsException {
        AssertUtil.canNotEmpty(tx, ErrorCode.NULL_PARAMETER);
        if (tx.getStatus() != TxStatusEnum.AGREED) {
            return;
        }
        List<TransactionService> serviceList = getServiceList(tx.getClass());
        for (TransactionService service : serviceList) {
            service.onCommit(tx, block);
        }
        tx.setStatus(TxStatusEnum.CONFIRMED);
    }

    @Override
    public void conflictDetectTx(Transaction tx, List<Transaction> txList) throws NulsException {
        AssertUtil.canNotEmpty(tx, ErrorCode.NULL_PARAMETER);
        List<TransactionService> serviceList = getServiceList(tx.getClass());
        for (TransactionService service : serviceList) {
            service.conflictDetect(tx, txList);
        }
        tx.setStatus(TxStatusEnum.AGREED);
    }

    @Override
    public void deleteTx(Transaction tx) {
        txDao.deleteTx(tx.getHash().getDigestHex());
    }

    @Override
    @DbSession
    public void deleteTx(long blockHeight) {
        List<TransactionPo> txList = txDao.getTxs(blockHeight);
        for (TransactionPo tx : txList) {
            txDao.deleteTx(tx.getHash());
        }
    }

    @Override
    public long getBlockReward(long blockHeight) {
        return txDao.getBlockReward(blockHeight);
    }

    @Override
    public Long getBlockFee(Long blockHeight) {
        return txDao.getBlockFee(blockHeight);
    }

    @Override
    public long getLastDayTimeReward() {
        return txDao.getLastDayTimeReward();
    }

    @Override
    public long getAccountReward(String address, long lastTime) {
        return txDao.getAccountReward(address, lastTime);
    }

    @Override
    public long getAgentReward(String address, int type) {
        return txDao.getAgentReward(address, type);
    }

    @Override
    public void unlockTxApprove(String txHash) {
//        boolean b = true;
//        int index = 0;
//        while (b) {
//            UtxoOutput output = ledgerCacheService.getUtxo(txHash + "-" + index);
//            if (output != null) {
//                if (OutPutStatusEnum.UTXO_CONFIRMED_CONSENSUS_LOCK == output.getStatus()) {
//                    output.setStatus(OutPutStatusEnum.UTXO_CONFIRMED_UNSPENT);
//                }
//                UtxoTransactionTool.getInstance().calcBalance(output.getAddress(), false);
//                index++;
//            } else {
//                b = false;
//            }
//        }
    }

    @Override
    @DbSession
    public void unlockTxSave(String txHash) {
        Log.info("-------------- exit agent unlockTxSave  ------------------txHash:" + txHash);
        txDao.unlockTxOutput(txHash);
//        String key = txHash + "-" + 0;
//        UtxoOutput output = ledgerCacheService.getUtxo(key);
//        output.setStatus(OutPutStatusEnum.UTXO_CONFIRMED_UNSPENT);
//        UtxoTransactionTool.getInstance().calcBalance(output.getAddress(), false);
    }

    @Override
    @DbSession
    public void unlockTxRollback(String txHash) {
        Log.info("-------------- exit agent unlockTxRollback  ------------------txHash:" + txHash);
        txDao.lockTxOutput(txHash);
//        UtxoOutput output = ledgerCacheService.getUtxo(txHash + "-" + 0);
//        if (output != null) {
//            if (OutPutStatusEnum.UTXO_CONFIRMED_UNSPENT == output.getStatus()) {
//                output.setStatus(OutPutStatusEnum.UTXO_CONFIRMED_CONSENSUS_LOCK);
//            }
//            UtxoTransactionTool.getInstance().calcBalance(output.getAddress(), false);
//        } else {
//            Map<String, Object> keyMap = new HashMap<>();
//            keyMap.put("tx_hash", txHash);
//            keyMap.put("out_index", 0);
//            UtxoOutputPo po = outputDataService.get(keyMap);
//            if (po != null) {
//                po.setStatus(UtxoOutputPo.LOCKED);
//                outputDataService.update(po);
//            }
//            output = UtxoTransferTool.toOutput(po);
//            ledgerCacheService.putUtxo(output.getKey(), output);
//        }
    }

    @Override
    public Page<UtxoOutput> getLockUtxo(String address, Integer pageNumber, Integer pageSize) {
        List<UtxoOutput> lockOutputs = new ArrayList<>();

        long count = outputDataService.getLockUtxoCount(address, TimeService.currentTimeMillis(), NulsContext.getInstance().getBestHeight(),
                NulsContext.getInstance().getGenesisBlock().getHeader().getTime());
        int start = (pageNumber - 1) * pageSize;
        if (lockOutputs.size() >= start + pageSize) {
            lockOutputs = lockOutputs.subList(start, start + pageSize);
            Page page = new Page(pageNumber, pageSize);
            page.setList(lockOutputs);
            page.setTotal(count);
            return page;
        } else if (start < lockOutputs.size()) {
            lockOutputs = lockOutputs.subList(start, lockOutputs.size());
            start = 0;
            pageSize = pageSize - lockOutputs.size();
        } else {
            start = start - lockOutputs.size();
        }
        List<UtxoOutputPo> poList = outputDataService.getLockUtxo(address, TimeService.currentTimeMillis(), NulsContext.getInstance().getBestHeight(),
                NulsContext.getInstance().getGenesisBlock().getHeader().getTime(), start, pageSize);
        for (UtxoOutputPo po : poList) {
            lockOutputs.add(UtxoTransferTool.toOutput(po));
        }
        Page page = new Page(pageNumber, pageSize);
        page.setTotal(count);
        page.setList(lockOutputs);
        return page;
    }

    public List<TransactionService> getServiceList(Class<? extends Transaction> txClass) {
        List<TransactionService> list = new ArrayList<>();
        Class clazz = txClass;
        while (!clazz.equals(Transaction.class)) {
            TransactionService txService = TransactionManager.getService(clazz);
            if (null != txService) {
                list.add(0, txService);
            }
            clazz = clazz.getSuperclass();
        }
        return list;
    }

    @Override
    public void resetLedgerCache() {
//        ledgerCacheService.clear();
//        UtxoCoinManager.getInstance().cacheAllUnSpendUtxo();
    }
}
