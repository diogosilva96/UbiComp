import RPi.GPIO as GPIO
import time
import threading
from w1thermsensor import W1ThermSensor
from bluetooth import *


class LED:
    def __init__(self, pin, name):
        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)
        GPIO.setup(pin, GPIO.OUT)
        self.pin = int(pin)
        self.name = str(name)
        self.OFF()

    def ON(self):
        self.state = 1
        GPIO.output(self.pin, GPIO.HIGH)
        print("["+self.name + "]: ON!")

    def OFF(self):
        self.state = 0
        GPIO.output(self.pin, GPIO.LOW)
        print("["+self.name + "]: OFF!")

    def getState(self):
        return self.state  # state = 0 off, state = 1 on

         
class UbiChair:
    def __init__(self):
        self.description = "[UbiChair]:"
        print("------------------------")
        print(self.description,"initial state: ")
        self.heater = LED(17, "Heater")  # chair's led to simulate the heater
        self.cooler = LED(18, "Cooler")  # chair's led to simulate the cooler
        print("------------------------")
        self.desiredTemp = 20  # desired temperature is inicialized as 20
        self.TempSensor = W1ThermSensor()  # temperature sensor
        threading.Thread(target=self.RegulateTemperature).start()


		
    def BTCommunication(self):
        description = "[CommunicationChannel]:"
        server_sock = BluetoothSocket(RFCOMM)
        server_sock.bind(("", PORT_ANY))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]
        uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"
        advertise_service(server_sock, "UbiChair",
				    service_id=uuid,
				    service_classes=[uuid, SERIAL_PORT_CLASS],
				    profiles=[SERIAL_PORT_PROFILE],
					 #                   protocols = [ OBEX_UUID ]
				    )
        while True:
            print(description,"Waiting for bluetooth connection on RFCOMM channel %d" % port)
            client_sock, client_info = server_sock.accept()
            print(description,"Accepted connection from ", client_info)
            try:
                data = client_sock.recv(1024)
                if len(data) == 0:
                    break
                print(description,"received [%s]" % data)
                data = data.decode('utf-8')
                auxdata = data.split("=")
                if auxdata[0] == 'desiredtemp':
					#data = "desiredtemp;"
                    self.desiredTemp = auxdata[1]
                actual_temp = self.TempSensor.get_temperature()
                aux = str(actual_temp).split(".")
                actual_temp = aux[0]
                data = "actualtemp="+str(actual_temp)+";"
                data.encode('utf-8')
                client_sock.send(data)
                print (description,"sending [%s]" % data)

            except IOError:
                pass

            except KeyboardInterrupt:
                print (description,"disconnected")
                client_sock.close()
                server_sock.close()
                print (description,"all done")
                break
	    
    def getDesiredTemp(self):
        return self.desiredTemp

    def PrintTemperature(self):
        print(self.description,"Actual temperature: ", self.TempSensor.get_temperature())
        print(self.description,"Desired temperature: ", self.getDesiredTemp())
        print("------------------------")
            


    def RegulateTemperature(self):
        while True:
            self.PrintTemperature()
            actualTemp = int(self.TempSensor.get_temperature())
            desiredTemp = int(self.desiredTemp)
            prevCoolerState = self.cooler.getState()
            prevHeaterState = self.heater.getState()
            if actualTemp > desiredTemp:
                if (prevCoolerState == 0):
                    self.cooler.ON()
                if (prevHeaterState == 1):
                    self.heater.OFF()
            elif actualTemp < desiredTemp:
                if (prevCoolerState == 1):
                    self.cooler.OFF()
                if (prevHeaterState == 0):
                    self.heater.ON()
            else:
                print("Temperature is fine!")
                if (prevCoolerState == 1):
                    self.cooler.OFF()
                if (prevHeaterState == 1):
                    self.heater.OFF()
            time.sleep(2)

if __name__ == "__main__":
    uc = UbiChair()
    uc.BTCommunication()







