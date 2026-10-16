import pickle
import subprocess

import requests


def run(user_input, payload, cursor):
    subprocess.run(user_input, shell=True)
    data = pickle.loads(payload)
    cursor.execute("SELECT * FROM t WHERE id = %s" % user_input)
    requests.get("https://example.com", verify=False)
    return data
