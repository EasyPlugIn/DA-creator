from flask import Flask
from flask import render_template
from flask import abort, redirect, url_for, request
from flask import send_from_directory

import subprocess as sub
import threading
import time
import traceback
from string import Template
import os

DOWNLOAD_FOLDER = 'downloads'

app = Flask(__name__)

public_thread_pool = { }

public_finish_pool = { }

public_output_pool = { }

public_error_pool = { }

def _milli_time ():
    return int(round(time.time()*1000))

@app.route('/')
def index_page ():
    template = 'da-creater-empty.html'
    return render_template(template, device_model_name_list=get_device_model_name_list())

@app.route('/da-creater/<device_model_name>')
def da_creater_page (device_model_name):
    template = 'da-creater-form.html'
    import urllib
    import json
    raw_data = json.loads(str(
            urllib.request.urlopen(
                'http://openmtc.darkgerm.com:7788/get_model_info_for_da',
                bytes('model_name={}'.format(device_model_name), 'utf8')
            ).readall(), 'utf8'
        ))

    context = {
        'device_model_name': device_model_name,
        'features': [i[0] for i in raw_data['idf'] + raw_data['odf']],
        'email': 'pi314.cs03g@nctu.edu.tw',
        'timestamp': str(_milli_time()),
        'device_model_name_list': get_device_model_name_list(),
    }
    return render_template(template, **context)

# @app.route('/compile/', methods=('GET', 'POST') )
# def gen_code_main ():
#     if request.method != 'POST':
#         return redirect('/monitor')
#
#     timestamp = _milli_time()
#     return redirect('/compile/{}'.format(timestamp), code=307)

@app.route('/monitor')
def monitor ():
    template = 'monitor.html'
    context = {
        'thread_pool': public_thread_pool,
        'finish_pool': public_finish_pool,
        'apk_list':    os.listdir('downloads'),
    }

    return render_template(template, **context)

@app.route('/compile/<timestamp>', methods=('GET', 'POST') )
def gen_code (timestamp):
    global public_thread_pool
    global public_finish_pool
    global public_output_pool
    global public_error_pool
    # global lowest_thread_num

    context = {'timestamp': timestamp, 'thread_pool': public_thread_pool, 'finish_pool': public_finish_pool}
    template = 'compile-message.html'

    if timestamp in public_finish_pool:
        if public_finish_pool[timestamp]:
            context['compile_message'] = public_output_pool[timestamp]
            context['download_link'] = '/download/{}.MorSensor.apk'.format(timestamp)
            #return HttpResponseRedirect( '/download/{}.MorSensor.apk'.format(timestamp) )

        else:
            context['compile_message'] = public_output_pool[timestamp] + ['Compile failed']
            context['compile_error_message'] = public_error_pool[timestamp]

        context['end'] = True
        del public_finish_pool[timestamp]
        del public_output_pool[timestamp]
        del public_error_pool[timestamp]

    elif timestamp in public_thread_pool:
        context['compile_message'] = public_output_pool[timestamp]

    elif timestamp not in public_thread_pool:
        if request.method != 'POST':
            return redirect('/')

        thread = threading.Thread(target=run_command, args=(timestamp, request.form))
        public_thread_pool[timestamp] = thread
        thread.daemon = True
        thread.start()

    return render_template(template, **context)

def run_command (timestamp, arguments):
    global public_thread_pool
    global public_finish_pool
    global public_output_pool
    global public_error_pool
    # global lowest_thread_num
    print('new thread start: {}'.format(timestamp))
    print('=====')
    session_id = 'session.{}'.format(timestamp)

    public_output_pool[timestamp] = []
    public_error_pool[timestamp] = []

    try:
        sub.check_output(['cp', '-r', 'workspace/sample', 'workspace/{}'.format(session_id)])
        render_code_template(session_id, arguments)
        p = sub.Popen(['sh', 'workspace/compile.sh', session_id], stdout=sub.PIPE, stderr=sub.PIPE, bufsize=1)
        while p.poll() == None:
            data = p.stdout.readline()
            public_output_pool[timestamp].append( wrap_compile_message(data) )
            time.sleep(0.3)

        # if timestamp > lowest_thread_num:
        #     lowest_thread_num = timestamp

        public_output_pool[timestamp].extend( [wrap_compile_message(i) for i in p.stdout.readlines()] )
        public_error_pool[timestamp].extend( [wrap_compile_message(i) for i in p.stderr.readlines()] )
        return_code = p.poll()
        if return_code == 0:
            public_finish_pool[timestamp] = True
            sub.check_output([
                'mv',
                'workspace/{}/MorSensor/build/outputs/apk/MorSensor-debug.apk'.format(session_id),
                'downloads/{}.MorSensor.apk'.format(timestamp)])

        else:
            public_finish_pool[timestamp] = False

    except Exception as e:
        print( traceback.format_exc() )
        public_finish_pool[timestamp] = False

    sub.check_output(['rm', '-r', 'workspace/{}'.format(session_id)])
    del public_thread_pool[timestamp]
    print('thread ends: {}'.format(timestamp))

def wrap_compile_message (line):
    if isinstance(line, bytes):
        line = str(line, 'utf-8')

    return line.rstrip('\n')

def render_code_template (session_id, arguments): # {{{
    f = open( 'workspace/{}/MorSensor/src/com/example/morsensor/Custom.java'.format(session_id), 'w' )
    code_template = Template('''
package com.example.morsensor;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.json.JSONArray;
import org.json.JSONException;

public class Custom {

    static final public String DEVICE_MODEL = "${device_model_name}";
    static final public String[] df_list = new String[]{ ${features_list} };
    static final public int ONE_DATA_SIZE = ${message_length};

    static public void device_initialize () {
        ${device_initialize}
    }

    /*
     * push data with DeFeMa.push_data( <feature_name>, <data> )
     * */
    static public int json_encode_and_push (byte[] raw_data) {
        int consumed_data_size = 0;
        ${upstream}
        return consumed_data_size;
    }

    /*
     * pull data with DeFeMa.pull_data( <feature_name> )
     * */
    static public String json_decoder () {
        ${downstream}
    }

    static public void device_terminate () {
        ${device_terminate}
    }

    /******************************************************************************/
    /******************************************************************************/
    /******************************************************************************/
    /****************************** Don't touch here ******************************/
    /******************************************************************************/

    static private String timestamp = "";
    static public DeviceAgent device;

    /* Don't touch here */
    static private void notify_message (String message) {
        device.logging(message);
    }

    static private void send_dataln (String data) throws IOException {
        if (device != null) {
            device.send_data(data + "\\n");
        }
    }

    static private void send_data (String data) throws IOException {
        if (device != null) {
            device.send_data(data);
        }
    }

    static private void send_data (byte[] data) throws IOException {
        if (device != null) {
            device.send_data(data);
        }
    }

}
''')
    f.write( code_template.substitute(
        device_model_name=arguments['device-model-name'],
        features_list='"' + '","'.join(arguments.getlist('feature[]')) + '"',
        message_length=arguments['message-length'],
        device_initialize=arguments['device-initialize'],
        upstream=arguments['upstream'],
        downstream=arguments['downstream'],
        device_terminate=arguments['device-terminate']
    ) )
    f.close()
    # }}}

@app.route('/download/<filename>')
def download (filename):
    return send_from_directory(DOWNLOAD_FOLDER, filename, as_attachment=True)

@app.route('/clean-downloads')
def clean_downloads ():
    for i in os.listdir('downloads'):
        os.remove('downloads/{}'.format(i))
    return redirect('/monitor')

def get_device_model_name_list ():
    import urllib
    import json
    raw_data = json.loads(str(
            urllib.request.urlopen(
                'http://openmtc.darkgerm.com:7788/get_model_list',
                bytes('', 'utf8')
            ).readall(), 'utf8'
        ))
    return raw_data

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000, debug=False)
