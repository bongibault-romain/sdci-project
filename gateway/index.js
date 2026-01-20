/**
 *  Author: Samir MEDJIAH medjiah@laas.fr
 *  File : gateway.js
 *  Version : 0.2.0
 */

var express = require('express')
var app = express()
app.use(express.json()) // for parsing application/json

var request = require('request');
const si = require('systeminformation');
const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');
const argv = yargs(hideBin(process.argv)).argv;
// --local_ip
// --local_port
// --local_name
// --remote_ip
// --remote_port
// --remote_name

var LOCAL_ENDPOINT = {IP : argv.local_ip, PORT : argv.local_port, NAME : argv.local_name};
var REMOTE_ENDPOINT = {IP : argv.remote_ip, PORT : argv.remote_port, NAME : argv.remote_name};

var REQUEST_PER_SECOND = argv.rate || 2;

const E_OK              = 200;
const E_CREATED         = 201;
const E_FORBIDDEN       = 403;
const E_NOT_FOUND       = 404;
const E_ALREADY_EXIST   = 500;


var db = {
        gateways : new Map()
    };

function addNewGateway(gw) {
    var res = -1;
    if (!db.gateways.get(gw.Name)) {
        db.gateways.set(gw.Name, gw);
        res = 0;
    }
    return res;
}

function removeGateway(gw) {
    if (db.gateways.get(gw.Name))
        db.gateways.delete(gw.Name);
}

// Rate limiting for outgoing requests
var outgoingRequestQueue = [];
var lastRequestTime = 0;
var requestInterval = 1000 / REQUEST_PER_SECOND; // milliseconds between requests

function processOutgoingQueue() {
    if (outgoingRequestQueue.length === 0) return;
    
    var now = Date.now();
    if (now - lastRequestTime >= requestInterval) {
        var item = outgoingRequestQueue.shift();
        lastRequestTime = now;
        request(item.options, item.callback);
        
        if (outgoingRequestQueue.length > 0) {
            setTimeout(processOutgoingQueue, requestInterval);
        }
    } else {
        setTimeout(processOutgoingQueue, requestInterval - (now - lastRequestTime));
    }
}

function doPOST(uri, body, onResponse) {
    outgoingRequestQueue.push({
        options: {method: 'POST', uri: uri, json : body},
        callback: onResponse
    });
    processOutgoingQueue();
}

function register() { 
    console.log('Registering gateway ' + LOCAL_ENDPOINT.NAME + ' to ' + REMOTE_ENDPOINT.NAME);
    
    doPOST(
        'http://' + REMOTE_ENDPOINT.IP + ':' + REMOTE_ENDPOINT.PORT + '/gateways/register', 
        {
            Name : LOCAL_ENDPOINT.NAME, 
            PoC : 'http://' + LOCAL_ENDPOINT.IP + ':' + LOCAL_ENDPOINT.PORT, 
        },
        function(error, response, respBody) {
            if (error) {
                console.log('Error registering gateway ' + LOCAL_ENDPOINT.NAME + ' to ' + REMOTE_ENDPOINT.NAME);
                console.log(error);
                console.log('Retrying in 2 seconds...');
                setTimeout(register, 2000);
                return;
            }

            console.log(respBody);
        }
    );
}

app.post('/rate/:rps', function(req, res) {
    console.log(req.url);
    console.log(req.body);
    var rps = parseFloat(req.params.rps);
    if (rps > 0) {
        REQUEST_PER_SECOND = rps;
        requestInterval = Math.floor(1000 / REQUEST_PER_SECOND);
        res.sendStatus(E_OK); 
    } else {
        res.sendStatus(E_FORBIDDEN); 
    }
})

app.post('/gateways/register', function(req, res) {
    console.log(req.url);
    console.log(req.body);
    var result = addNewGateway(req.body);

    if (result === 0)
        res.sendStatus(E_CREATED);  
    else
        res.sendStatus(E_ALREADY_EXIST);  
 });
app.post('/devices/register', function(req, res) {
    console.log(req.url);
    console.log(req.body);

    res.sendStatus(E_OK);
    
    doPOST(
        'http://' + REMOTE_ENDPOINT.IP + ':' +REMOTE_ENDPOINT.PORT + '/devices/register',
        req.body,
        function(error, response, respBody) {
            if (error) {
                console.log('Error forwarding device registration: ' + error);
            } else {
                console.log('Device registration forwarded: ' + respBody);
            }
        }
    )
 });
 app.post('/device/:dev/data', function(req, res) {
    console.log(req.body);
    var dev = req.params.dev;

    res.status(202).send({status: 'accepted', timestamp: Date.now()});
    
    doPOST(
        'http://' + REMOTE_ENDPOINT.IP + ':' +REMOTE_ENDPOINT.PORT + '/device/' + dev + '/data',
        req.body,
        function(error, response, respBody) {
            if (error) {
                console.log('Error forwarding data from ' + dev + ': ' + error);
            } else {
                console.log('Data from ' + dev + ' forwarded: ' + respBody);
            }
        }
    )
});
app.get('/gateways', function(req, res) {
    console.log(req.url);
    console.log(req.body);
    let resObj = [];
    db.gateways.forEach((v,k) => {
        resObj.push(v);
    });
    res.send(resObj);
});
app.get('/gateway/:gw', function(req, res) {
    console.log(req.url);
    console.log(req.body);
    var gw = req.params.gw;
    var gateway = db.gateways.get(gw);
    if (gateway)
        res.status(E_OK).send(JSON.stringify(gateway));
    else
        res.sendStatus(E_NOT_FOUND);
});

app.get('/ping', function(req, res) {
    console.log(req.url);
    console.log(req.body);
    res.status(E_OK).send({pong: Date.now()});
});
app.get('/health', function(req, res) {
    console.log(req.url);
    console.log(req.body);
    si.currentLoad((d) => {
        console.log(d);
        res.status(E_OK).send(JSON.stringify(d));
    })
});


register();
app.listen(LOCAL_ENDPOINT.PORT , function () {
    console.log(LOCAL_ENDPOINT.NAME + ' listening on : ' + LOCAL_ENDPOINT.PORT );
});