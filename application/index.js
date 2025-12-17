/**
 *  Author: Samir MEDJIAH medjiah@laas.fr
 *  File : application.js
 *  Version : 0.2.0
 */

var express = require('express')
var app = express()
var request = require('request');

const yargs = require('yargs/yargs');
const { hideBin } = require('yargs/helpers');
const argv = yargs(hideBin(process.argv)).argv;
// --remote_ip
// --remote_port
// --device_name
// --send_period 

var REMOTE_ENDPOINT = { IP: argv.remote_ip, PORT: argv.remote_port };
var DATA_PERIOD = argv.send_period;
var TARGTE_DEVICE = argv.device_name;



function retrieveData() {
    request(
        {
            method: 'GET',
            uri: 'http://' + REMOTE_ENDPOINT.IP + ':' + REMOTE_ENDPOINT.PORT + '/device/' + TARGTE_DEVICE + '/latest',
        },
        function (error, response, respBody) {
            console.log(respBody);
        }
    );
}

setInterval(retrieveData, DATA_PERIOD);