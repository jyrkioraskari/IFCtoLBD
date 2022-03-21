package de.rwth_aachen.dc.mvdXMLOnlineChecker.events;

import com.google.common.eventbus.EventBus;

/*
* 
Jyrki Oraskari, Aalto University, 2016 

This research has partly been carried out at Aalto University in DRUMBEAT 
“Web-Enabled Construction Lifecycle” (2014-2017) —funded by Tekes, 
Aalto University, and the participating companies.

The MIT License (MIT)
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/


public class EventBusCommunication {
	private final EventBus commucication_bus = new EventBus();
	
	
	private static EventBusCommunication instance = null;

	
	protected EventBusCommunication() {
		commucication_bus.register(this);
	}

	public static EventBusCommunication getInstance() {
		if (instance == null) {
			instance = new EventBusCommunication();
		}
		return instance;
	}

	
	public void post( Object  event) {
		commucication_bus.post(event);
	}
	
	

	public void register(Object subscriber)
	{
		commucication_bus.register(subscriber);
	}

	
}
