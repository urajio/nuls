package io.nuls.network.message.handler;

import io.nuls.core.event.BaseNetworkEvent;
import io.nuls.network.entity.Node;
import io.nuls.network.message.NetworkEventResult;

/**
 * @author vivi
 * @date 2017/11/21
 */
public interface NetWorkEventHandler {
    NetworkEventResult process(BaseNetworkEvent event, Node node);
}
