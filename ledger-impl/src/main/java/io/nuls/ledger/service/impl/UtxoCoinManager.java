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

import io.nuls.core.utils.log.Log;
import io.nuls.db.dao.UtxoOutputDataService;
import io.nuls.db.entity.UtxoOutputPo;
import io.nuls.ledger.entity.UtxoBalance;
import io.nuls.ledger.entity.UtxoOutput;
import io.nuls.ledger.util.UtxoTransactionTool;
import io.nuls.ledger.util.UtxoTransferTool;
import io.nuls.protocol.context.NulsContext;
import io.nuls.protocol.model.Na;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * cache and operate all unspend utxo
 */
public class UtxoCoinManager {

    private static UtxoCoinManager instance = new UtxoCoinManager();

    private UtxoCoinManager() {
    }

    public static UtxoCoinManager getInstance() {
        return instance;
    }

    private UtxoOutputDataService outputDataService = NulsContext.getServiceBean(UtxoOutputDataService.class);

    private Lock lock = new ReentrantLock();

//    public void cacheAllUnSpendUtxo() {
//        List<UtxoOutputPo> utxoOutputPos = outputDataService.getAllUnSpend();
//        Set<String> addressSet = new HashSet<>();
//
//        for (int i = 0; i < utxoOutputPos.size(); i++) {
//            UtxoOutputPo po = utxoOutputPos.get(i);
//            UtxoOutput output = UtxoTransferTool.toOutput(po);
//            ledgerCacheService.putUtxo(output.getKey(), output);
//            addressSet.add(po.getAddress());
//        }
//
//        for (String str : addressSet) {
//            UtxoTransactionTool.getInstance().calcBalance(str, false);
//        }
//    }

    /**
     * Utxo is used to ensure that each transaction will not double
     *
     * @param address
     * @param value
     * @return
     */
    public List<UtxoOutput> getAccountUnSpend(String address, Na value) {
       return null;
    }

    public List<UtxoOutput> getAccountsUnSpend(List<String> addressList, Na value) {
        List<UtxoOutput> unSpends = new ArrayList<>();
        try {
            //check use-able is enough , find unSpend utxo
            Na amount = Na.ZERO;
            boolean enough = false;
            for (String address : addressList) {
                List<UtxoOutputPo> poList = outputDataService.getAccountUnSpend(address);
                if (poList == null || poList.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < poList.size(); i++) {
                    UtxoOutputPo output = poList.get(i);
                    if (output.isLocked()) {
                        continue;
                    }
                    unSpends.add(UtxoTransferTool.toOutput(output));
                    amount = amount.add(Na.valueOf(output.getValue()));
                    if (amount.isGreaterOrEquals(value)) {
                        enough = true;
                        break;
                    }
                }
                if (enough) {
                    break;
                }
            }
            if (!enough) {
                unSpends = new ArrayList<>();
            }
        } catch (Exception e) {
            Log.error(e);
            unSpends = new ArrayList<>();
        }
        return unSpends;
    }

}
