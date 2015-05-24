import os
import config
import pymongo
import random
import json
from parse import *
from flask import Flask, request, render_template, redirect, send_file, session

import PIL
from PIL import Image
from PIL import ImageFont
from PIL import ImageDraw
from PIL import ImageEnhance

import requests

MONGODB_URI = 'mongodb://IbmCloud_5nqjhrgl_7t5tt0nf_qp9ngbs9:RNtYSU_ABXsMT7RnORKNxfPGkKKGPrTC@ds037802.mongolab.com:37802/IbmCloud_5nqjhrgl_7t5tt0nf'

AUTH_API_KEY = '32f041cf-cfde-463e-aca2-5d1cb8fca071'
API_KEY = '32a173ca-7544-49a3-a7c7-18735798272d'

OCR_URL = 'http://api.idolondemand.com/1/api/sync/ocrdocument/v1'
AUTH_URL = 'https://api.idolondemand.com/1/api/sync/authenticate/v1'

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'static'


def processImage(filename):
    img = Image.open(os.path.join(app.config['UPLOAD_FOLDER'], filename))
    img2 = img.rotate(-90)
    img2.save(os.path.join(app.config['UPLOAD_FOLDER'],filename))
    img = Image.open(os.path.join(app.config['UPLOAD_FOLDER'], filename))
    gray = img.convert('L')
    bw = gray.point(lambda x:255 if x < 128 else 0, '1')
    #enhancer = ImageEnhance.Sharpness(img)
    #im = enhancer.enhance(2.0)
    #enhancer = ImageEnhance.Contrast(img)
    #im1 = enhancer.enhance(4.0)
    #draw = ImageDraw.Draw(img)
    #draw.text((0, 0),"Sample Text",(0,0,0))
    bw.save(os.path.join(app.config['UPLOAD_FOLDER'], "changed.jpg"))

def makeRequest(filename):
    files = {'file': open(os.path.join(app.config['UPLOAD_FOLDER'], filename), 'rb')}
    payload = {'apikey': API_KEY}
    r1 = requests.post(OCR_URL, data = payload, files = files)
    jsonresp = json.loads(r1.text)
    if len(jsonresp['text_block']) > 0:
        docresponse = jsonresp['text_block'][0]['text']
    else:
        docresponse = "No response"
    #payload = {'apikey': API_KEY, 'mode': 'subtitle'}
    #files = {'file': open(os.path.join(app.config['UPLOAD_FOLDER'], filename), 'rb')}
    #r2 = requests.post(OCR_URL, data = payload, files = files)
    #jsonresp = json.loads(r2.text)
    #subresponse = ''
    #for entry in jsonresp['text_block']:
    #    subresponse += entry['text']
    return docresponse# + subresponse

def createNoteImage(text):
    client = pymongo.MongoClient(MONGODB_URI)  
    db = client.get_default_database()
    notes = db['notes']
    cursor = notes.find()
    for doc in cursor:
         if doc['title'].lower() in text.lower():
            return doc['notes']
    return "No Note Found"

def getDimensions(filename):
    img = Image.open(os.path.join(app.config['UPLOAD_FOLDER'], filename))
    files = {'file': open(os.path.join(app.config['UPLOAD_FOLDER'], filename), 'rb')}
    payload = {'apikey': API_KEY, 'mode': 'subtitle'}
    r1 = requests.post(OCR_URL, data = payload, files = files)
    jsonresp = json.loads(r1.text)
    height = 0
    left = 10000
    for text in jsonresp['text_block']:
        if text['height'] + text['top'] > height:
            height = text['height'] + text['top']
        if text['left'] < left:
            left = text['left']

    return height, left


def drawOnImage(filename, text, height, left):
    img = Image.open(os.path.join(app.config['UPLOAD_FOLDER'], filename))
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype("arial.ttf", 16) 
    startx = left
    starty = height + 10
    offset = 10
    if isinstance(text, list):
        for line in text:
            draw.text((startx, starty + offset),line,(0,0,0), font=font)
            offset += 20
    else:
        draw.text((startx, starty + offset),text,(0,0,0), font=font)
    img.save(os.path.join(app.config['UPLOAD_FOLDER'], "output.jpg"))

def getUserNotes(user):
    client = pymongo.MongoClient(MONGODB_URI)
    db = client.get_default_database()
    notes = db['notes']
    cursor = notes.find({'owner': user})
    notes = []
    for doc in cursor:
        notes.append(doc)
    return notes

@app.route('/upload',methods=['GET','POST'])
def Upload():
    return render_template('upload.html')

@app.route('/getAR',methods=['GET','POST'])
def GetAR():
    if request.method == 'POST':
        file = request.files['pic']
        filename = file.filename
        file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
        processImage(filename)
        text  =  makeRequest(filename)
        text2 = makeRequest("changed.jpg")
        height, left = getDimensions(filename)
        returnnote = createNoteImage(text + text2)
        drawOnImage(filename, returnnote, height, left)
        return send_file((os.path.join(app.config['UPLOAD_FOLDER'], "output.jpg")), mimetype='image/jpg')
    else:
        return "Y U NO USE POST?"

@app.route('/getText',methods=['GET','POST'])
def GetText():
    if request.method == 'POST':
        file = request.files['pic']
        filename = file.filename
        file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
        processImage(filename)
        text  =  makeRequest(filename)
        text2 = makeRequest("changed.jpg")
        return text + text2
    else:
        return "Y U NO USE POST?"



@app.route('/getNoteText',methods=['GET','POST'])
def GetNoteText():
    if request.method == 'POST':
        file = request.files['pic']
        filename = file.filename
        file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
        processImage(filename)
        text  =  makeRequest(filename)
        #text2 = makeRequest("changed.jpg")
        height, left = getDimensions(filename)
        returnnote = createNoteImage(text)# + text2)
        if returnnote == "No Note Found":
            returndata = {}
            returndata['success'] = False
            return json.dumps(returndata)
        returndata = {}
        returndata['success'] = True
        returndata['data'] = returnnote
        returndata['left'] = left
        returndata['height'] = height
        return json.dumps(returndata)
    else:
        return "Y U NO USE POST?"

@app.route('/ismongoup')
def TestMongoConnectivity():
    client = pymongo.MongoClient(MONGODB_URI)
    db = client.get_default_database()
    songs = db['notes']
    cursor = songs.find({'title': 'Sports'})
    abc = ''
    for doc in cursor:
        abc = abc + str(doc['notes'])
    return abc

@app.route('/')
def Index():
    return render_template('index.html')

@app.route('/signup')
def SignUp():
    return "Baah... Too sleepy to code this up! Just ask one of us for a valid userid/pass :P <br/> <br/> -Navin 'm@dMAx' Pai"

@app.route('/saveNote', methods=["POST"])
def saveNote():
    if request.method == 'POST':
        lines = request.form['data'].split("\n") 
        client = pymongo.MongoClient(MONGODB_URI)
        db = client.get_default_database()
        notes = db['notes']
        cursor = notes.update({'title': request.form.get('title')},{"$set":{"notes": lines}})
        result = {}
        result['data'] = lines
        return json.dumps(result)

@app.route('/createNote', methods=["POST"])
def createNote():
    if request.method == 'POST':
        note = {}
        note['notes'] = request.form['data'].split("\n") 
        note['title'] = request.form['title']
        note['owner'] = session['user']
        client = pymongo.MongoClient(MONGODB_URI)
        db = client.get_default_database()
        notes = db['notes']
        cursor = notes.insert(note)
        result = {}
        result['data'] = note['notes']
        result['title'] = note['title']
        return json.dumps(result)



@app.route('/deleteNote', methods=["POST"])
def deleteNote():
    if request.method == 'POST':
        client = pymongo.MongoClient(MONGODB_URI)
        db = client.get_default_database()
        notes = db['notes']
        cursor = notes.delete_one({'title': request.form.get('title')})
        return "success"


@app.route('/loadNote')
def loadNote():
    client = pymongo.MongoClient(MONGODB_URI)
    db = client.get_default_database()
    notes = db['notes']
    cursor = notes.find({'title': request.args.get('title')})
    text = []
    for doc in cursor:
        resp = {}
        resp['data'] = doc['notes']
        return json.dumps(resp)

@app.route('/home')
def Home():
    if 'user' in session:
        notes = getUserNotes(session['user'])
        return render_template('home.html', user = session['user'], notes = notes)
    else:
        return redirect('/')

@app.route('/login', methods=['GET', 'POST'])
def Login():
    if request.method == 'POST':
        params={'mechanism': 'simple','store':'clickie-stickie','user':request.form['login'],'password':request.form['password'], 'apikey': AUTH_API_KEY}
        r = requests.post(AUTH_URL, data = params)
        jsonresp = json.loads(r.text)
        if 'success' in jsonresp and jsonresp['success']:
            session['user'] = jsonresp['token']['data']['user']
            return redirect("/home")           
        return render_template('login.html', error_message="invalid credentials")  
    return render_template('login.html')

port = os.getenv('VCAP_APP_PORT', '5000')


if __name__ == "__main__":
    app.secret_key = 'A0Zr98j/3yX R~XHH!jmN]LWX/,?RT'
    app.run(host='0.0.0.0', port=int(port), debug=True)
    
