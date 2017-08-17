package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.utils.Pointer;
import com.idevicesinc.sweetblue.utils.Util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.Semaphore;


@Config(manifest = Config.NONE, sdk = 25)
@RunWith(RobolectricTestRunner.class)
public class GattRefreshTest extends BaseBleUnitTest
{

    @Test(timeout = 12000)
    public void connectThenRefreshGattTest() throws Exception
    {

        m_config.runOnMainThread = false;
        m_config.loggingOptions = LogOptions.ON;

        m_mgr.setConfig(m_config);

        final Pointer<Boolean> refreshingGatt = new Pointer<>(false);


        m_mgr.setListener_Discovery(new DiscoveryListener()
        {
            @Override public void onEvent(DiscoveryEvent e)
            {
                if (e.was(LifeCycle.DISCOVERED) || e.was(LifeCycle.REDISCOVERED))
                {
                    e.device().connect(new DeviceStateListener()
                    {
                        @Override public void onEvent(StateEvent e)
                        {
                            if (e.didEnter(BleDeviceState.SERVICES_DISCOVERED))
                            {
                                if (refreshingGatt.value)
                                {
                                    succeed();
                                }
                            }
                            if (e.didEnter(BleDeviceState.INITIALIZED))
                            {
                                refreshingGatt.value = true;
                                e.device().refreshGattDatabase();
                            }
                        }
                    });
                }
            }
        });

        m_mgr.newDevice(Util.randomMacAddress(), "Test Device");

        startTest();

    }

}
