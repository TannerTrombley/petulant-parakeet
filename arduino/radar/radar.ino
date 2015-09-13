
#include <ArduinoJson.h>
#include <Wire.h>
#include <LIDARLite.h>
#include <SoftwareSerial.h>

//CONSTANTS
short CONSECUTIVE_READINGS = 5;
//short DANGER_THRESHOLD = 3; // m/s

LIDARLite lidar;

//void printToBt(int num)
//{
//  root["Measurement"] = "Distance";
//  root["cm"] = num;
//  root.prettyPrintTo(Serial);
//  
//}


//confirm that there is no more danger behind the cyclist
//--------
//return true if not safe
//return false if safe
//Need a certain number of consecutive negative readings to be considered safe
bool confirmSafety()
{
   for (int i = 0; i < CONSECUTIVE_READINGS; ++i)
   {
    //check if not safe
    if (lidar.velocity() > 0)
      return true;
   }
   //if we get here it is safe, return false
   return false;
}

//confirm that there is danger to the cyclist
//-------
//return true if there is danger
//return false if it was a false danger indicator
bool confirmDanger(float &avg)
{
   avg = 1;
   float total = 0;
   for (int i = 0; i < CONSECUTIVE_READINGS; ++i)
   {
    //check if safe
    int reading = lidar.velocity();
    total += reading;
    if (reading <= 0)
      return false;
   }
   //if we get here there is danger, return true
   //set the avg velocity as well
   avg = total / CONSECUTIVE_READINGS;
   return true;
}

void sendDangerMessage(float velocity, float distance)
{
  StaticJsonBuffer<200> jsonBuffer;
  JsonObject& root = jsonBuffer.createObject();

  root["status"] = "danger";
  root["velocity"] = velocity;
  root["distance"] = distance;
  root.printTo(Serial);
}

void sendSafeMessage()
{
  StaticJsonBuffer<200> jsonBuffer;
  JsonObject& root = jsonBuffer.createObject();

  root["status"] = "safe";
  root.printTo(Serial);
}


void setup()
{

  lidar.begin();
  //velocity scaling to m/s
  lidar.scale(4);
  Serial.begin(9600);
}


//Poll the velocity of any object from behind the bike. If
// the velocity reads positive and is beyond a certain threshold then take a 
// series of readings that will confirm the object behind is real. Store the average of those velocity measurements
// average out a number of distance measurements and send those along with the velocity over BT

//STATE
//--Hold a danger and a safe state. When safe look for danger, when in danger check for safety
bool inDanger = false;
void loop()
{
  float avgVel;
  bool prevState = inDanger;
  int current = lidar.velocity();
  if (inDanger && current < 0)
    inDanger = confirmSafety();
  else if (!inDanger && current >= 0)
    inDanger = confirmDanger(avgVel);
  //No other scenrio should cause a change in state

  //TODO send something based on the current  vs prevState
  if (prevState != inDanger)
  {
    if (inDanger)
    {
      float total = 0;
      for (int i = 0; i < CONSECUTIVE_READINGS; ++i)
        total += lidar.distance();
      sendDangerMessage(avgVel, total/CONSECUTIVE_READINGS); 
    }
    else
    {
      sendSafeMessage();
    }
  } 
}

