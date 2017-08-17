package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.utils.GattDatabase;
import com.idevicesinc.sweetblue.utils.Pointer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.UUID;
import static org.junit.Assert.assertFalse;


@Config(manifest = Config.NONE, sdk = 25)
@RunWith(RobolectricTestRunner.class)
public final class DefaultTransactionsTest extends BaseBleUnitTest
{


    private final static UUID mAuthServiceUuid = UUID.randomUUID();
    private final static UUID mAuthCharUuid = UUID.randomUUID();
    private final static UUID mInitServiceUuid = UUID.randomUUID();
    private final static UUID mInitCharUuid = UUID.randomUUID();

    private GattDatabase db = new GattDatabase().addService(mAuthServiceUuid)
            .addCharacteristic(mAuthCharUuid).setValue(new byte[] { 0x2, 0x4 }).setProperties().read().setPermissions().read().completeService()
            .addService(mInitServiceUuid)
            .addCharacteristic(mInitCharUuid).setValue(new byte[] { 0x8, 0xA }).setProperties().read().setPermissions().read().completeService();


    @Test(timeout = 40000)
    public void defaultAuthTransactionTest() throws Exception
    {
        startTest(false);

        m_config.runOnMainThread = false;
        m_config.defaultScanFilter = e -> ScanFilter.Please.acknowledgeIf(e.name_native().contains("Test Device"));
        m_config.defaultAuthFactory = () -> new BleTransaction.Auth()
        {
            @Override protected void start(BleDevice device)
            {
                final BleRead read = new BleRead(mAuthServiceUuid, mAuthCharUuid).setReadWriteListener(e ->
                {
                    if (e.wasSuccess())
                    {
                        succeed();
                    }
                    else
                    {
                        assertFalse(e.status().toString(), true);
                    }
                });
                device.read(read);
            }
        };

        m_config.loggingOptions = LogOptions.ON;

        connectToMultipleDevices(m_config, false);

        m_mgr.stopScan();
        m_mgr.disconnectAll();

        m_config.runOnMainThread = true;

        connectToMultipleDevices(m_config, true);

        m_mgr.stopScan();
        m_mgr.disconnectAll();
    }

    @Test(timeout = 40000)
    public void defaultInitTransactionTest() throws Exception
    {
        startTest(false);

        m_config.runOnMainThread = false;
        m_config.defaultScanFilter = e -> ScanFilter.Please.acknowledgeIf(e.name_native().contains("Test Device"));
        m_config.defaultInitFactory = () -> new BleTransaction.Init()
        {
            @Override protected void start(BleDevice device)
            {
                BleRead read = new BleRead(mInitServiceUuid, mInitCharUuid).setReadWriteListener(e ->
                {
                    if (e.wasSuccess())
                    {
                        succeed();
                    }
                    else
                    {
                        assertFalse(e.status().toString(), true);
                    }
                });
                device.read(read);
            }
        };

        m_config.loggingOptions = LogOptions.ON;

        connectToMultipleDevices(m_config, false);

        m_mgr.stopScan();
        m_mgr.disconnectAll();

        m_config.runOnMainThread = true;

        connectToMultipleDevices(m_config, true);

        m_mgr.stopScan();
        m_mgr.disconnectAll();
    }

    @Test(timeout = 40000)
    public void defaultAuthAndInitTransactionTest() throws Exception
    {
        startTest(false);

        m_config.runOnMainThread = false;
        m_config.defaultScanFilter = e -> ScanFilter.Please.acknowledgeIf(e.name_native().contains("Test Device"));
        m_config.defaultInitFactory = () -> new BleTransaction.Init()
        {
            @Override protected void start(BleDevice device)
            {
                BleRead read = new BleRead(mInitServiceUuid, mInitCharUuid).setReadWriteListener(e ->
                {
                    if (e.wasSuccess())
                    {
                        succeed();
                    }
                    else
                    {
                        assertFalse(e.status().toString(), true);
                    }
                });
                device.read(read);
            }
        };
        m_config.defaultAuthFactory = () -> new BleTransaction.Auth()
        {
            @Override protected void start(BleDevice device)
            {
                BleRead read = new BleRead(mAuthServiceUuid, mAuthCharUuid).setReadWriteListener(e ->
                {
                    if (e.wasSuccess())
                    {
                        succeed();
                    }
                    else
                    {
                        assertFalse(e.status().toString(), true);
                    }
                });
                device.read(read);
            }
        };

        m_config.loggingOptions = LogOptions.ON;

        connectToMultipleDevices(m_config, false);

        m_mgr.stopScan();
        m_mgr.disconnectAll();

        m_config.runOnMainThread = true;

        connectToMultipleDevices(m_config, true);

        m_mgr.disconnectAll();
        m_mgr.stopScan();
    }

    private void connectToMultipleDevices(BleManagerConfig config, final boolean completeTest) throws Exception
    {
        m_mgr.setConfig(config);

        m_mgr.setListener_Discovery(new DiscoveryListener()
        {
            final Pointer<Integer> connected = new Pointer(0);

            @Override public void onEvent(DiscoveryEvent e)
            {
                if (e.was(LifeCycle.DISCOVERED) || e.was(LifeCycle.REDISCOVERED))
                {
                    e.device().connect(e1 ->
                    {
                        if (e1.didEnter(BleDeviceState.INITIALIZED))
                        {
                            connected.value++;
                            System.out.println(e1.device().getName_override() + " connected. #" + connected.value);
                            if (connected.value == 3)
                            {
                                if (completeTest)
                                {
                                    succeed();
                                }
                                else
                                {
                                    release();
                                }
                            }
                        }
                    });
                }
            }
        });

        m_mgr.setListener_State(e ->
        {
            if (e.didExit(BleManagerState.STARTING_SCAN) && e.didEnter(BleManagerState.SCANNING))
            {
                NativeUtil.advertiseNewDevice(m_mgr, -45, "Test Device #1");
                NativeUtil.advertiseNewDevice(m_mgr, -35, "Test Device #2");
                NativeUtil.advertiseNewDevice(m_mgr, -60, "Test Device #3");
            }
        });

        m_mgr.startScan();
        reacquire();
    }

    @Override
    public P_GattLayer getGattLayer(BleDevice device)
    {
        return new UnitTestGatt(device, db);
    }

}
