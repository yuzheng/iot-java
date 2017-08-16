package com.cht.iot.demo.pi;
 
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.persistence.entity.api.IAttribute;
import com.cht.iot.persistence.entity.api.IDevice;
import com.cht.iot.persistence.entity.api.IIdStatus;
import com.cht.iot.persistence.entity.api.ISensor;
import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.service.api.OpenMqttClient;
import com.cht.iot.service.api.OpenRESTfulClient;
import com.cht.iot.util.JsonUtils;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
 
public class Main {
	static final Logger LOG = LoggerFactory.getLogger(Main.class);
	
	// GPIO
	final GpioController gpio;
	
	final String host = "iot.cht.com.tw"; // CHANGE TO ONLINE SERVER	
	
	final String restfulHost = "211.20.181.194";
	final int restfulPort = 80;
	private OpenRESTfulClient restful;
	
	final int mqttPort = 1883;
	final int keepAliveInterval = 10;
	boolean isBye = false;
	private OpenMqttClient mqtt;
	
	final String DHT11 = "11";
	final String DHT22 = "22";
	
	private String apiKey = null; // CHANGE TO YOUR PROJECT API KEY
	
	String deviceId = "388622157"; // CHANGE TO YOUR DEVICE ID
	
	final String configFilename = "iot-config.properties";
	
	private Properties productProperties = null;
	private Properties iotProperties = null;
	
	ExecutorService executor = Executors.newSingleThreadExecutor();
	
	Scanner input = new Scanner(System.in);
	
	public Main() {
		// 建立GPIO控制物件
		gpio = GpioFactory.getInstance();
	}
	
	/*
	 * 輸入一"產品資訊設定檔"，包含產品產品代碼,流水號, 與要產生的設備和感測器基本資訊
	 * 產品資訊設定檔於執行此應用程式時, 透過 args 參數帶入(例如：-f product.properties)
	 */
	public void init(Properties prop) {
		productProperties = prop;
		iotProperties = productProperties;
		
		if(!checkConfig(configFilename)){  //檢查是否已存在 configFilename
			// do onConfigure
			String code = productProperties.getProperty("code", null);
			String sn = productProperties.getProperty("sn", null);
			if(code != null && sn != null){
				// 因為尚未建立產品設備資訊, 故進行設備自動組態設定流程, 訂閱 MQTT 的 registry
				mqttReconfigureTopic(code, sn);
			}else{
				LOG.info("Reconfigure Properites Missing: code or sn. (" + code + ", " + sn + ")");
			}
		}else{  // has apikey
			LOG.info("The config file ("+configFilename+") is existed!");
			
			//載入已建立的產品設備資訊(apikey, device_id, sesnor_id, ...)
			iotProperties = readProperties(System.getProperty("user.dir")+File.separator+configFilename);
			
			//set apikey
			apiKey = iotProperties.getProperty("apikey", null);
			//set deviceId
			deviceId = iotProperties.getProperty("device_id", null);
			
			LOG.info("load iotProperties:");
			LOG.info("apikey:"+apiKey);
			LOG.info("device_id:"+deviceId);
			
			if(apiKey != null && deviceId != null){
				
				// 啟動Sensor處理
				startSensorProcess();
			}else{
				LOG.warn("Not found of apikey or device_id in the confing file!");
			}
		}
	}
	
	/*
	 * 檢查是否已存在 configFilename
	 */
	public boolean checkConfig(String filename){
		if(new File(System.getProperty("user.dir")+File.separator+filename).exists()){
			Properties prop = readProperties(System.getProperty("user.dir")+File.separator+filename);
			String key = prop.getProperty("apikey", null);
    		if(key == null){
    			return false;
    		}
			return true;
		}else{
			return false;
		}
	}
	
	private void startSensorProcess(){
		// init mqtt
		initMqtt();
		// subscribe mqtt topics
		//mqttSubscribeTopic();
		
		// init RESTful
		initRestful();
		
		// run custom process
		test();
	}
	
	public void test(){
		/*
		// 建立控制GPIO_01 (pin#12)輸出的物件
        final GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(
                        RaspiPin.GPIO_01, "My LED", PinState.LOW);
 
        LOG.info("LED ON...");
 
        // 設定GPIO_01的狀態，設定為true表示這個針腳會輸出3.3V的電壓
        pin.setState(true);
        delay(3000);
 
        LOG.info("LED OFF...");
 
        // 設定GPIO_01的狀態，設定為fasle表示這個針腳不會輸出電壓
        pin.setState(false);
        */
		Thread thread = new Thread(new Runnable() {
			public void run() {
	           while(true){
	        	 //input = new Scanner(System.in);
	        	   String answer = input.nextLine();
	           		System.out.println("answer:"+answer);
	           		if(answer.equalsIgnoreCase("bye")){
	           			isBye = true;
	           			System.out.println("Yea, bye bye!");
	           		}else{
	           			System.out.println("Awww :(");
	           		}
				}
			}
		});
		thread.setDaemon(true);
	    thread.start();
		
		delay(5000);
		LOG.info("start dht22...");
		while(!isBye){
			dhtSensor(DHT22,"18");
			delay(60000);
		}
		
		System.exit(0);
	}
	
	//=== Restful ===
	private void initRestful(){
		restful = new OpenRESTfulClient(restfulHost, restfulPort, apiKey); // save or query the value
	}
	
	//=== mqtt part start ===
	private void initMqtt(){
		mqtt = new OpenMqttClient(host, mqttPort, apiKey); // MQTT to listen the value changed
		mqtt.setKeepAliveInterval(keepAliveInterval);
		
		mqtt.start(); 
	}
	
	// subscribe the lamp & bracelet (www.mi.com [Xiaomi China])
	protected void mqttSubscribeTopic() {
		if(iotProperties.containsKey("sensor_id")){
			
			String properties_sensor_id = iotProperties.getProperty("sensor_id", "");
			LOG.info("mqttSubscribeTopic:"+properties_sensor_id);
			if(properties_sensor_id.equals("")){
				LOG.warn("Empty of Sensor Id");
			}else{
				String [] sensors = properties_sensor_id.split(",");
				List<String> topics = new ArrayList<String>(sensors.length);
				
				for(String sensor_id : sensors){
					System.out.println(" ==> " + sensor_id);
					topics.add(OpenMqttClient.getRawdataTopic(deviceId, sensor_id));
				}
				mqtt.setTopics(topics);
				
				mqtt.setListener(new OpenMqttClient.ListenerAdapter() {			
					@Override
					public void onRawdata(String topic, Rawdata rawdata) {
						handleOnRawdata(rawdata);
					}
				});
				
				//mqtt.start(); // wait for incoming message from IoT platform
			}
		}else{
			LOG.warn("Not Found Sensor Id");
		}
		
	}
	
	private void mqttReconfigureTopic(String code, String sn){
		initMqtt();
		
		LOG.info("mqttReconfigureTopic:"+code+","+sn);
		
		String reconfigureTopic = OpenMqttClient.getRegistryTopic(code+sn);
		
		mqtt.setTopics(Arrays.asList(reconfigureTopic));
		
		mqtt.setListener(new OpenMqttClient.ListenerAdapter() {			
			@Override
			public void onReconfigure(String topic, String _apiKey) {
				LOG.info(_apiKey);
				handleOnReconfigure(_apiKey);
			}
		});
		
		mqtt.start(); // wait for incoming message from IoT platform
	}
	
	protected void handleOnRawdata(Rawdata rawdata) {
		LOG.info("onRawdata - {}", JsonUtils.toJson(rawdata));
		
		if (deviceId.equals(rawdata.getDeviceId())) {
			String id = rawdata.getId();
			LOG.info("sensor rawdata:"+id);
			
			// customer message handler
			/*
			if (lampSensorId.equals(id)) {
				String value = rawdata.getValue()[0];
				
				lamp.setState(isOn(value)? PinState.LOW : PinState.HIGH);
				
			} else if (braceletSensorId.equals(id)) {				
				vibrateBracelet(); // to your bracelet (www.mi.com [Xiaomi China])
				
			} else if (shutterSensorId.equals(id)) {
				saveSnapshot(); // take a picture
			}
			*/
		}
	}
	
	protected void handleOnReconfigure(String _apiKey) {
		
		apiKey = _apiKey;
		LOG.info("OnReconfigure - "+apiKey);  //JsonUtils.toJson(rawdata)
		
		iotProperties.setProperty("apikey", apiKey);
		
		mqtt.stop();
		//initMqtt();
		
		configureDevice();
	}
	
	//=== mqtt part end ===
	
	private void configureDevice(){
		LOG.info("configureDevice");
		
		initRestful();
		
		IDevice dev = new IDevice();
		IAttribute[] attributes = {new IAttribute("sn",iotProperties.getProperty("sn")),new IAttribute("code",iotProperties.getProperty("code"))};
		
		String deviceName = "Raspberry Pi Dev";
		String deviceDesc = "About Raspberry Pi Dev";
		if(productProperties.containsKey("device_name")){
			if(!productProperties.getProperty("device_name").trim().equals("")){
				deviceName = productProperties.getProperty("device_name");
			}
		}
		if(productProperties.containsKey("device_desc")){
			if(!productProperties.getProperty("device_desc").trim().equals("")){
				deviceDesc = productProperties.getProperty("device_desc");
			}
		}
		dev.setName(deviceName);
		dev.setDesc(deviceDesc);
		dev.setType("general");
		dev.setAttributes(attributes);
		
		try {
			dev = restful.saveDevice(dev,IIdStatus.class);
			if(dev!=null){
				deviceId = dev.getId();
				LOG.info("device id:"+deviceId);
				iotProperties.setProperty("device_id", deviceId);
			
				writeProperties(configFilename, iotProperties);   //save properties
				
				settingSensor();
			}else{
				LOG.warn("Problem for creating device!");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void settingSensor(){
		//create dht11 sensor
		/*
		IAttribute[] attributes = {};
		ISensor temperatureSensor = new ISensor();
		temperatureSensor.setId("temperature");
		temperatureSensor.setName("溫度");
		temperatureSensor.setDesc("DHT11感測器的溫度資料");
		temperatureSensor.setType("gauge");
		temperatureSensor.setUnit("度");
		temperatureSensor.setAttributes(attributes);
		configureSensor(temperatureSensor);
		
		iotProperties = addProperties(iotProperties, "sensor_id", "temperature", false);
		
		ISensor humiditySensor = new ISensor();
		humiditySensor.setId("humidity");
		humiditySensor.setName("濕度");
		humiditySensor.setDesc("DHT11感測器的濕度資料");
		humiditySensor.setType("gauge");
		humiditySensor.setUnit("度");
		humiditySensor.setAttributes(attributes);
		configureSensor(humiditySensor);
		
		iotProperties = addProperties(iotProperties, "sensor_id", "humidity", false);
		*/
		IAttribute[] attributes = {};
		String sensor_id = productProperties.getProperty("sensor_id", null);
		if(sensor_id != null){
			String[] sensor_ids = sensor_id.split(",");
			for(String id : sensor_ids){
				ISensor isensor = new ISensor();
				isensor.setId(id);
				isensor.setName(productProperties.getProperty("sensor_name_"+id, id));
				isensor.setDesc(productProperties.getProperty("sensor_desc_"+id, id));
				isensor.setType(productProperties.getProperty("sensor_type_"+id, "gauge"));
				isensor.setUnit(productProperties.getProperty("sensor_unit_"+id, ""));
				isensor.setAttributes(attributes);
				configureSensor(isensor);
				
				iotProperties = addProperties(iotProperties, "sensor_id", id, false);
			}
			
			writeProperties(configFilename, iotProperties);   //save properties
			
			//startSensorProcess
			startSensorProcess();
		}else{
			LOG.warn("Not found sensor_id in the product properties file");
		}
	}
	
	private void configureSensor(ISensor sensor){
		try {
			sensor = restful.saveSensor(deviceId, sensor);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private Properties addProperties(Properties prop, String key, String value, boolean overwrite){
		
		if(prop.containsKey(key)){
			if(overwrite){
				prop.setProperty(key, value);
			}else{
				prop.setProperty(key, prop.getProperty(key)+","+value);
			}
		}else{
			prop.setProperty(key, value);
		}
		
		return prop;
	}
	
	// using Adafruit_Python_DHT : https://github.com/adafruit/Adafruit_Python_DHT
	private void dhtSensor(String dht, String gpio){  //dht 11, 12, gpio
		try {
			Process proc= Runtime.getRuntime().exec(new String[] {"python", "/home/pi/py_project/Adafruit_Python_DHT/examples/AdafruitDHT.py", dht, gpio});
			//Process proc= Runtime.getRuntime().exec("ls -al");
			if (proc != null) {
	            proc.waitFor();
	            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))){
					String line;
					while((line = br.readLine()) != null){
						LOG.info("DHT-"+dht+":" + line);
						String[] sensorData = line.split(",");
						if(sensorData.length == 2){
							for(String data : sensorData){
								String[] info = data.split("=");
								if(info.length == 2){
									if(info[0].equals("Temp")){
										mqtt.save(deviceId, "temperature", new String[] {info[1]});
										//restful.saveRawdata(deviceId, "temperature", info[1]);
									}else if(info[0].equals("Humidity")){
										mqtt.save(deviceId, "humidity", new String[] {info[1]});
										//restful.saveRawdata(deviceId, "humidity", info[1]);
									}
								}
							}
						}
					}
					br.close();
				}
	        }else{
	        	LOG.info("process is null");
	        }
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void delay(int ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            LOG.info(e.toString());
        }
    }
	
	public void destroy() {
		 LOG.info("Bye...");
		 
		// 最後記的要關閉GPIO
        gpio.shutdown();
	}
    
    /*
     * properties
     */ 
    public static Properties readProperties(String filename){
    	Properties prop = new Properties();
    	//InputStream input = null;
    	Reader inStream = null;
    	
    	try {
    		//input = new FileInputStream(filename); //"config.properties"
    		inStream = new InputStreamReader(new FileInputStream(filename), "UTF-8");
    		// load a properties file
    		prop.load(inStream);
    		

    		// get the property value and print it out
    		if(prop.containsKey("apikey")){
    			LOG.info("readProperties - apikey:"+prop.getProperty("apikey"));
    		}else{
    			prop.setProperty("apikey", "");
    		}
    		//LOG.info("readProperties - code:" + prop.getProperty("code"));
    		//LOG.info("readProperties - sn:" + prop.getProperty("sn"));

    		return prop;
    		
    	} catch (IOException ex) {
    		ex.printStackTrace();
    	} finally {
    		if (inStream != null) {
    			try {
    				inStream.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    		}
    	}
    	return null;
    }
    
    public void writeProperties(String filename, Properties prop){
		
		OutputStream output = null;

		try {
			LOG.info("user.home: "+System.getProperty("user.home"));
			LOG.info("user.dir: "+System.getProperty("user.dir"));
			//LOG.info("java.class.path: "+System.getProperty("java.class.path"));
			output = new FileOutputStream(System.getProperty("user.dir")+File.separator+filename);

			// set the properties value
			//prop.setProperty("deviceid", "123");
			//prop.setProperty("apikey", "");
			//prop.setProperty("dbpassword", "password");

			// save properties to project root folder
			prop.store(output, null);

		} catch (IOException io) {
			io.printStackTrace();
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
    }
    
    public static void main(String[] args) throws Exception {
    	String propertiesFile = "";
    	Properties prop = null;
    	for(int i = 0; i < args.length; i++) {
            if(args[i].equals("-f")){
            	propertiesFile = args[i+1];
            }
        }
    	
    	if(new File(propertiesFile).exists()){
    		prop = readProperties(propertiesFile);
    	}
    	
    	Main m = new Main();
    	try {
			m.init(prop);			
			Object lck = new Object(); // wait and see
			synchronized (lck) {
				lck.wait();
			}			
		} finally {
			m.destroy();
		}
    	//m.init(prop);
    }
}