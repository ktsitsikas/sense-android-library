<?php
include_once("db_connect.php");
include_once("sendToDeviceServiceManager.php");
include_once("deviceID_check.php");
$tbl_name="sensor_data"; // Table name

// Get input
$sensorName		= $_REQUEST['sensorName'];
$sensorValue		= $_REQUEST['sensorValue'];
$sensorDataType 	= $_REQUEST['sensorDataType'];
$sensorDeviceType       = $_REQUEST['sensorDeviceType'];

if(isset($_REQUEST['sensorDataType']))
  $sensorDataType 	= $_REQUEST['sensorDataType'];
else
  $sensorDataType 	= 'string';

if(isset($_REQUEST['sampleTime']))
  $time     = $_REQUEST['sampleTime'];
else
  $time = microtime(true);

if($sensorName && $sensorValue)
{
	// To protect MySQL injection (more detail about MySQL injection)	
	$sensorName 		= stripslashes($sensorName);	
	$sensorValue 		= stripslashes($sensorValue);
	$sensorDataType 	= stripslashes($sensorDataType);
	$sensorDeviceType 	= stripslashes($sensorDeviceType);
	

	// Check if the sensor exists	
	$sql	= "SELECT * FROM sensor_type WHERE name = '$sensorName' and device_type = '$sensorDeviceType'";
	$result	= mysql_query($sql);	
	$count	= mysql_num_rows($result);

	// Get sensorType	
	if($count == 1)	
	{
		$row 		= mysql_fetch_assoc($result);	
		$sensorTypeID 	= $row['id'];
	}
	// Create new sensor type
	else
	{	
		$sql	= "INSERT INTO sensor_type (`id` ,`name`, `data_type`, `device_type` ) VALUES (NULL ,  '$sensorName', '$sensorDataType','$sensorDeviceType')";
		$result	= mysql_query($sql);
		if($result)
		{
			// Get sensorType
			$sql		= "SELECT * FROM sensor_type WHERE name = '$sensorName' and device_type = '$sensorDeviceType'";
			$result		= mysql_query($sql);	
			$row 		= mysql_fetch_assoc($result);	
			$sensorTypeID 	= $row['id'];
		}
		else
		{	
			$message  = 'Invalid query: ' . mysql_error() . "\n";
			$message .= 'Whole query: ' . $query;
			die($message);
		}			
	}
	
	// Insert into DB	
	$sql	= "INSERT INTO $tbl_name (`id` ,`device_id` ,`sensor_type` ,`sensor_value` ,`date`) VALUES (NULL ,  '$deviceId', '$sensorTypeID',  '$sensorValue',  '$time')";
	$result	= mysql_query($sql);
	if($result)
	{
		echo "OK\n";
		// send to device service manager
		sendToDeviceServiceManager($deviceId, $sensorDataType, $sensorName, $sensorValue);
	}
	else
	{	
		$message  = 'Invalid query: ' . mysql_error() . "\n";
		$message .= 'Whole query: ' . $query;
	        die($message);
	}	
}
else
	echo "Error: no sensorName or sensorValue given";

?>
