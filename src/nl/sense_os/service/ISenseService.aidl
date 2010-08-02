package nl.sense_os.service;

import nl.sense_os.service.ISenseServiceCallback;

interface ISenseService 
{	
    void getStatus(ISenseServiceCallback callback);
	String serviceResponse();
    void setDeviceId(String id);
    void setUpdateFreq(int freq);
	void setUrl(String url);
	void toggleDeviceProx(boolean active, ISenseServiceCallback callback);
	void toggleLocation(boolean active, ISenseServiceCallback callback);
	void toggleMotion(boolean active, ISenseServiceCallback callback);
	void toggleNoise(boolean active, ISenseServiceCallback callback);
    void togglePhoneState(boolean active, ISenseServiceCallback callback);
    void togglePopQuiz(boolean active, ISenseServiceCallback callback);
    boolean serviceLogin();
    boolean serviceRegister();
}

