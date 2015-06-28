Amp
===

-Look! The zipped Tester file allows us to simulate a connection between two phones.

--SIMULATION TESTING--

	-We can set the following for the connection simulation:
	
		minimum delay - Lowest amount of delay possible 
		
		Range of swing - An integer value the size of the range of the randomly generated delay. This range of values starts at minimum delay.
	
	- TO DO - We should have a measurement tool as an Android app, or built in to the Amp app that allows us to measure these values
	(minimum delay and range of swing) for two devices. This would allow us to more accurately set these config values. At the moment
	these values are naive assumptions about connection strength.
	
--SIMULATION TEST ANALYSIS-- 

	-Through a simple statistical analysis of a series of simulation trials (1 hours worth gives what I think is a reliably accurate body of data),
	we can deduce the chance of success on each handshake attempt. This allows us to figure out:
	
		-The number of handshake attempts to be made to have a certain probability of success.
			-i.e. 1000 handshake attempts with a .5% chance of success on each attempt gives a 99% chance for at least one successful
			handshake.
			
			
- TO DO - --PLUGGING IT IN TO AMP--

	-Must transplant the handshake algorithm from the simulation into Amp.
	
	-Given our assumptions about average connection strength are accurate, this should function the same as in the simulation.