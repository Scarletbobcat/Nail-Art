import pymongo
import datetime
import os
from dotenv import load_dotenv

load_dotenv()

connection_string = os.getenv("MONGO_URI")

client = pymongo.MongoClient(connection_string)

db = client["Nail-Art"]

appointments = db["Appointments"]

appointments_same_start_end = appointments.find({"$expr": {"$eq": ["$startTime", "$endTime"]}}).to_list()

if (not appointments_same_start_end):
  print("No appointments with same start and end time.")
else:
  print(appointments_same_start_end)