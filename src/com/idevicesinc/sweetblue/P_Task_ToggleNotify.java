package com.idevicesinc.sweetblue;

import java.util.UUID;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.ReadWriteEvent;
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.Status;
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.Target;
import com.idevicesinc.sweetblue.P_PollManager.E_NotifyState;
import com.idevicesinc.sweetblue.utils.Utils;
import com.idevicesinc.sweetblue.utils.Uuids;
import com.idevicesinc.sweetblue.BleManager.UhOhListener.UhOh;

class P_Task_ToggleNotify extends PA_Task_ReadOrWrite implements PA_Task.I_StateListener
{
	private static enum Type
	{
		NOTIFY,
		INDICATE
	}
	
	private final boolean m_enable;
	private final UUID m_descUuid;
	
	private byte[] m_writeValue = null;
	
	public P_Task_ToggleNotify(BleDevice device, UUID uuid, boolean enable, P_WrappingReadWriteListener writeListener)
	{
		this(device, uuid, enable, writeListener, null);
	}
	
	private P_Task_ToggleNotify(BleDevice device, UUID uuid, boolean enable, P_WrappingReadWriteListener writeListener, PE_TaskPriority priority)
	{
		super(device, uuid, writeListener, false, null, priority);
		
		m_descUuid = Uuids.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_UUID;
		m_enable = enable;
	}
	
	private byte[] getWriteValue()
	{
		return m_writeValue != null ? m_writeValue : BleDevice.EMPTY_BYTE_ARRAY;
	}
	
	static byte[] getWriteValue(BluetoothGattCharacteristic char_native, boolean enable)
	{
		Type type = null;
		
		if( (char_native.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0x0 )
		{
			type = Type.NOTIFY;
		}
		else if( (char_native.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0x0 )
		{
			type = Type.INDICATE;
		}
		else
		{
			type = Type.NOTIFY;
		}
		
		final byte[] enableValue = type == Type.NOTIFY ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
		final byte[] disableValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
		return enable ? enableValue : disableValue;
	}

	@Override public void execute()
	{
		super.execute();
		
		BluetoothGattCharacteristic char_native = getDevice().getNativeCharacteristic(m_uuid);
		
		if( char_native == null )
		{
			this.fail(Status.NO_MATCHING_TARGET, BleStatuses.GATT_STATUS_NOT_APPLICABLE, Target.CHARACTERISTIC, m_uuid, ReadWriteEvent.NON_APPLICABLE_UUID);
			
			return;
		}
		
		if( !getDevice().getNativeGatt().setCharacteristicNotification(char_native, m_enable) )
		{
			this.fail(Status.FAILED_TO_TOGGLE_NOTIFICATION, BleStatuses.GATT_STATUS_NOT_APPLICABLE, Target.CHARACTERISTIC, m_uuid, ReadWriteEvent.NON_APPLICABLE_UUID);
			
			return;
		}
		
		BluetoothGattDescriptor descriptor = char_native.getDescriptor(m_descUuid);
		
		if( descriptor == null )
		{
			this.fail(Status.NO_MATCHING_TARGET, BleStatuses.GATT_STATUS_NOT_APPLICABLE, Target.DESCRIPTOR, m_uuid, m_descUuid);
			
			return;
		}
		
		m_writeValue = getWriteValue(char_native, m_enable);
		
		if( !descriptor.setValue(getWriteValue()) )
		{
			this.fail(Status.FAILED_TO_SET_VALUE_ON_TARGET, BleStatuses.GATT_STATUS_NOT_APPLICABLE, Target.DESCRIPTOR, m_uuid, m_descUuid);
			
			return;
		}
		
		if( !getDevice().getNativeGatt().writeDescriptor(descriptor) )
		{
			this.fail(Status.FAILED_TO_SEND_OUT, BleStatuses.GATT_STATUS_NOT_APPLICABLE, Target.DESCRIPTOR, m_uuid, m_descUuid);
			
			return;
		}
	}
	
	@Override protected void fail(Status status, int gattStatus, Target target, UUID charUuid, UUID descUuid)
	{
		if( m_enable )
		{
			getDevice().getPollManager().onNotifyStateChange(m_uuid, E_NotifyState.NOT_ENABLED);
		}
		
		super.fail(status, gattStatus, target, charUuid, descUuid);
	}
	
	@Override protected void succeed()
	{
		ReadWriteEvent result = newResult(Status.SUCCESS, BluetoothGatt.GATT_SUCCESS, Target.DESCRIPTOR, m_uuid, m_descUuid); 
//		getDevice().addWriteTime(result.totalTime);
		
		if( m_enable )
		{
			getDevice().getPollManager().onNotifyStateChange(m_uuid, E_NotifyState.ENABLED);
		}
		else
		{
			getDevice().getPollManager().onNotifyStateChange(m_uuid, E_NotifyState.NOT_ENABLED);
		}
		
		getDevice().invokeReadWriteCallback(m_readWriteListener, result);
		 
		super.succeed();
	}
	
	public void onDescriptorWrite(BluetoothGatt gatt, UUID descUuid, int status)
	{
		 getManager().ASSERT(gatt == getDevice().getNativeGatt());
		 
		 if( !descUuid.equals(m_descUuid) )  return;

		 if( Utils.isSuccess(status) )
		 {
			 succeed();
		 }
		 else
		 {
			 fail(Status.REMOTE_GATT_FAILURE, status, Target.DESCRIPTOR, m_uuid, descUuid);
		 }
	}
	
	@Override public void onStateChange(PA_Task task, PE_TaskState state)
	{
		super.onStateChange(task, state);
		
		if( state == PE_TaskState.TIMED_OUT )
		{
			m_logger.w(m_logger.charName(m_uuid) + " descriptor write timed out!");
			
			getDevice().invokeReadWriteCallback(m_readWriteListener, newResult(Status.TIMED_OUT, BleStatuses.GATT_STATUS_NOT_APPLICABLE, Target.DESCRIPTOR, m_uuid, m_descUuid));
			
			getManager().uhOh(UhOh.WRITE_TIMED_OUT);
		}
		else if( state == PE_TaskState.SOFTLY_CANCELLED )
		{
			Target target = this.getState() == PE_TaskState.EXECUTING ? Target.DESCRIPTOR : Target.CHARACTERISTIC;
			UUID descUuid = target == Target.DESCRIPTOR ? m_descUuid : ReadWriteEvent.NON_APPLICABLE_UUID;
			getDevice().invokeReadWriteCallback(m_readWriteListener, newResult(getCancelType(), BleStatuses.GATT_STATUS_NOT_APPLICABLE, target, m_uuid, descUuid));
		}
	}
	
	@Override protected UUID getDescUuid()
	{
		return m_descUuid;
	}
	
	@Override public boolean isMoreImportantThan(PA_Task task)
	{
		return isMoreImportantThan_default(task);
	}
	
	private BleDevice.ReadWriteListener.Type getReadWriteType()
	{
		return m_enable ? BleDevice.ReadWriteListener.Type.ENABLING_NOTIFICATION : BleDevice.ReadWriteListener.Type.DISABLING_NOTIFICATION;
	}

	@Override protected ReadWriteEvent newResult(Status status, int gattStatus, Target target, UUID charUuid, UUID descUuid)
	{
		return new ReadWriteEvent(getDevice(), charUuid, descUuid, getReadWriteType(), target, getWriteValue(), status, gattStatus, getTotalTime(), getTotalTimeExecuting());
	}
	
	@Override protected BleTask getTaskType()
	{
		return BleTask.TOGGLE_NOTIFY;
	}
}
