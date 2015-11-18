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
import shutil
import urllib
import json
import os


DOWNLOAD_FOLDER = 'downloads'
CUSTOM_CODE_FOLDER = 'workspace/custom/'

app = Flask(__name__)
lock = threading.Semaphore()
thread_pool = {}
compile_message = {}    # compile message buffer
compile_result = {}     # compile result (status code)


def _milli_time():
    return int(round(time.time()*1000))


@app.route('/')
def index_page():
    template = 'da-creator-empty.html'
    return render_template(template, device_model_name_list=get_device_model_name_list())


@app.route('/da-creator/<device_model_name>', methods=('GET', 'POST'))
def da_creator(device_model_name):
    global thread_pool

    lock.acquire()
    compiling_session_exists = device_model_name in thread_pool
    lock.release()

    if request.method == 'GET' and not compiling_session_exists:
        # no compiling session exists
        return da_creator_form(device_model_name)

    elif request.method == 'POST' and compiling_session_exists:
        # recreate compiling session, not allowed
        return da_creator_form(device_model_name)

    context = {}
    template = 'compile-message.html'

    if request.method == 'GET' and compiling_session_exists:
        # there is an existing compiling session
        context['compile_message'] = compile_message[device_model_name]
        context['compile_result'] = compile_result[device_model_name]

        if compile_result[device_model_name] in ('S', 'F'):
            # compiling process ended, clean up
            lock.acquire()
            del thread_pool[device_model_name]
            del compile_result[device_model_name]
            lock.release()

    elif request.method == 'POST' and not compiling_session_exists:
        # creating a compiling session
        lock.acquire()
        custom_codes = {}
        custom_codes['device-initialize'] = request.form['device-initialize']
        custom_codes['device2easyconnect'] = request.form['device2easyconnect']
        custom_codes['easyconnect2device'] = request.form['easyconnect2device']
        custom_codes['device-terminate'] = request.form['device-terminate']
        t = threading.Thread(target=worker, args=(device_model_name, custom_codes))
        t.daemon = True
        thread_pool[device_model_name] = t
        lock.release()
        t.start()

    return render_template(template, **context)


def da_creator_form(device_model_name):
    template = 'da-creator-form.html'
    raw_data = json.loads(str(
            urllib.request.urlopen(
                'http://openmtc.darkgerm.com:7788/get_model_info_for_da',
                bytes('model_name={}'.format(device_model_name), 'utf8')
            ).readall(), 'utf8'
        ))

    if not os.path.exists('{}/{}'.format(CUSTOM_CODE_FOLDER, device_model_name)):
        os.mkdir('{}/{}'.format(CUSTOM_CODE_FOLDER, device_model_name))
        os.mkmod('{}/{}/deviceInitialize'.format(CUSTOM_CODE_FOLDER, device_model_name))
        os.mkmod('{}/{}/device2EasyConnect'.format(CUSTOM_CODE_FOLDER, device_model_name))
        os.mkmod('{}/{}/easyConnect2Device'.format(CUSTOM_CODE_FOLDER, device_model_name))
        os.mkmod('{}/{}/deviceTerminate'.format(CUSTOM_CODE_FOLDER, device_model_name))

    context = {
        'device_model_name': device_model_name,
        'features': [i[0] for i in raw_data['idf'] + raw_data['odf']],
        'timestamp': str(_milli_time()),
        'device_model_name_list': get_device_model_name_list(),

        'code_device_initialize': open('{}/{}/deviceInitialize'.format(CUSTOM_CODE_FOLDER, device_model_name)).read(),
        'code_device2easyconnect': open('{}/{}/device2EasyConnect'.format(CUSTOM_CODE_FOLDER, device_model_name)).read(),
        'code_easyconnect2device': open('{}/{}/easyConnect2Device'.format(CUSTOM_CODE_FOLDER, device_model_name)).read(),
        'code_device_terminate': open('{}/{}/deviceTerminate'.format(CUSTOM_CODE_FOLDER, device_model_name)).read(),

        'email': 'pi314.cs03g@nctu.edu.tw',
    }
    return render_template(template, **context)


# @app.route('/compile/', methods=('GET', 'POST') )
# def gen_code_main():
#     if request.method != 'POST':
#         return redirect('/monitor')
#
#     timestamp = _milli_time()
#     return redirect('/compile/{}'.format(timestamp), code=307)


def worker(device_model_name, custom_codes):
    global compile_message
    global compile_result

    compile_message[device_model_name] = []
    compile_result[device_model_name] = 'C'     # 'C'ompiling  'S'uccess  'F'ailed

    print('worker', device_model_name)
    save_files(device_model_name, custom_codes)
    setup_project(device_model_name)
    compile_result[device_model_name] = 'S' if compile_project(device_model_name) == 0 else 'F'
    clean_project(device_model_name)
    print('end')


def save_files(device_model_name, custom_codes):
    with open('{}/{}/deviceInitialize'.format(CUSTOM_CODE_FOLDER, device_model_name), 'w') as f:
        f.write(custom_codes['device-initialize'])

    with open('{}/{}/device2EasyConnect'.format(CUSTOM_CODE_FOLDER, device_model_name), 'w') as f:
        f.write(custom_codes['device2easyconnect'])

    with open('{}/{}/easyConnect2Device'.format(CUSTOM_CODE_FOLDER, device_model_name), 'w') as f:
        f.write(custom_codes['easyconnect2device'])

    with open('{}/{}/deviceTerminate'.format(CUSTOM_CODE_FOLDER, device_model_name), 'w') as f:
        f.write(custom_codes['device-terminate'])


def setup_project(device_model_name):
    shutil.copytree('workspace/template', 'workspace/{}'.format(device_model_name))


def compile_project(device_model_name):
    p = sub.Popen(['sh', 'workspace/compile.sh', device_model_name], stdout=sub.PIPE, bufsize=1)
    while p.poll() == None:
        data = p.stdout.readline()
        compile_message[device_model_name].append(str(data, 'utf-8'))

    return_code = p.poll()
    return return_code


def clean_project(device_model_name):
    shutil.rmtree('workspace/{}'.format(device_model_name))


@app.route('/monitor')
def monitor():
    template = 'monitor.html'
    context = {
        'thread_pool': thread_pool,
        # 'finish_pool': finish_pool,
        'apk_list':    os.listdir('downloads'),
    }
    return render_template(template, **context)


@app.route('/download/<filename>')
def download(filename):
    return send_from_directory(DOWNLOAD_FOLDER, filename, as_attachment=True)


@app.route('/clean-downloads')
def clean_downloads():
    for i in os.listdir('downloads'):
        os.remove('downloads/{}'.format(i))
    return redirect('/monitor')


def get_device_model_name_list():
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
    # app.run(host='0.0.0.0', port=8000, debug=False)
    app.run(host='0.0.0.0', port=8000, debug=True)
