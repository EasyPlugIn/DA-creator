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


DOWNLOAD_PATH = 'downloads'
WORKSPACE_PATH = 'workspace'
CUSTOM_CODE_PATH = os.path.join(WORKSPACE_PATH, 'custom')
BACKUP_POSTFIX = 'backup'

app = Flask(__name__)
lock = threading.Semaphore()
thread_pool = {}
compile_message = {}    # compile message buffer
compile_result = {}     # compile result (status code)


def join(*args, **kwargs):
    return os.path.join(*args, **kwargs)


def get_model_custom_code_path(dm_name):
    return join(CUSTOM_CODE_PATH, dm_name)


def _milli_time():
    return int(round(time.time()*1000))


@app.route('/')
def index_page():
    template = 'da-creator-empty.html'
    return render_template(template, dm_name_list=get_dm_name_list())


@app.route('/da-creator/<dm_name>', methods=('GET', 'POST'))
def da_creator(dm_name):
    global thread_pool

    lock.acquire()
    compiling_session_exists = dm_name in thread_pool
    lock.release()

    if request.method == 'GET' and not compiling_session_exists:
        # no compiling session exists
        return da_creator_form(dm_name)

    elif request.method == 'POST' and compiling_session_exists:
        # recreate compiling session, not allowed
        return da_creator_form(dm_name)

    context = {'dm_name_list': get_dm_name_list()}
    template = 'compile-message.html'

    if request.method == 'GET' and compiling_session_exists:
        # there is an existing compiling session
        context['compile_message'] = compile_message[dm_name]
        context['compile_result'] = compile_result[dm_name]

        if compile_result[dm_name] in ('S', 'F'):
            # compiling process ended, clean up
            lock.acquire()
            del thread_pool[dm_name]
            del compile_result[dm_name]
            lock.release()

    elif request.method == 'POST' and not compiling_session_exists:
        # creating a compiling session
        lock.acquire()
        custom_codes = {}
        custom_codes['device-initialize'] = request.form['device-initialize']
        custom_codes['device2easyconnect'] = request.form['device2easyconnect']
        custom_codes['easyconnect2device'] = request.form['easyconnect2device']
        custom_codes['device-terminate'] = request.form['device-terminate']
        t = threading.Thread(target=worker, args=(dm_name, custom_codes))
        t.daemon = True
        thread_pool[dm_name] = t
        lock.release()
        t.start()

    return render_template(template, **context)


def da_creator_form(dm_name):
    template = 'da-creator-form.html'
    raw_data = json.loads(str(
            urllib.request.urlopen(
                'http://openmtc.darkgerm.com:7788/get_model_info_for_da',
                bytes('model_name={}'.format(dm_name), 'utf8')
            ).readall(), 'utf8'
        ))

    model_custom_code_path = get_model_custom_code_path(dm_name)
    if not os.path.isdir(model_custom_code_path):
        # first editing this device model, create empty custom code
        os.mkdir(model_custom_code_path)
        open(join(model_custom_code_path, 'deviceInitialize'), 'w').close()
        open(join(model_custom_code_path, 'device2EasyConnect'), 'w').close()
        open(join(model_custom_code_path, 'easyConnect2Device'), 'w').close()
        open(join(model_custom_code_path, 'deviceTerminate'), 'w').close()

    context = {
        'dm_name': dm_name,
        'features': [i[0] for i in raw_data['idf'] + raw_data['odf']],
        'dm_name_list': get_dm_name_list(),

        'code_device_initialize': open(join(model_custom_code_path, 'deviceInitialize')).read(),
        'code_device2easyconnect': open(join(model_custom_code_path, 'device2EasyConnect')).read(),
        'code_easyconnect2device': open(join(model_custom_code_path, 'easyConnect2Device')).read(),
        'code_device_terminate': open(join(model_custom_code_path, 'deviceTerminate')).read(),

        'email': 'pi314.cs03g@nctu.edu.tw',
    }
    return render_template(template, **context)


def worker(dm_name, custom_codes):
    global compile_message
    global compile_result

    compile_message[dm_name] = []
    compile_result[dm_name] = 'C'     # 'C'ompiling  'S'uccess  'F'ailed

    print(dm_name, 'worker')
    update_custom_code(dm_name, custom_codes)
    setup_project(dm_name)
    compile_result[dm_name] = 'S' if compile_project(dm_name) == 0 else 'F'
    if compile_result[dm_name] == 'S':
        clean_backup_files(dm_name)
    else:
        recover_custom_code(dm_name)

    clean_project(dm_name)
    print(dm_name, 'end')


def update_custom_code(dm_name, custom_codes):
    model_custom_code_path = get_model_custom_code_path(dm_name)
    shutil.copytree(
            model_custom_code_path,
            '{}.{}'.format(model_custom_code_path, BACKUP_POSTFIX))

    with open(join(model_custom_code_path, 'deviceInitialize'), 'w') as f:
        f.write(custom_codes['device-initialize'])

    with open(join(model_custom_code_path, 'device2EasyConnect'), 'w') as f:
        f.write(custom_codes['device2easyconnect'])

    with open(join(model_custom_code_path, 'easyConnect2Device'), 'w') as f:
        f.write(custom_codes['easyconnect2device'])

    with open(join(model_custom_code_path, 'deviceTerminate'), 'w') as f:
        f.write(custom_codes['device-terminate'])


def clean_backup_files(dm_name):
    shutil.rmtree('{}.{}'.format(get_model_custom_code_path(dm_name), BACKUP_POSTFIX))


def recover_custom_code(dm_name):
    shutil.rmtree(get_model_custom_code_path(dm_name))


def setup_project(dm_name):
    shutil.rmtree('workspace/{}'.format(dm_name), ignore_errors=True)
    shutil.copytree('workspace/template', 'workspace/{}'.format(dm_name))


def compile_project(dm_name):
    p = sub.Popen(['sh', 'workspace/compile.sh', dm_name], stdout=sub.PIPE, bufsize=1)
    while p.poll() == None:
        data = p.stdout.readline()
        compile_message[dm_name].append(str(data, 'utf-8'))

    return_code = p.poll()
    return return_code


def clean_project(dm_name):
    shutil.rmtree('workspace/{}'.format(dm_name))


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
    return send_from_directory(DOWNLOAD_PATH, filename, as_attachment=True)


@app.route('/clean-downloads')
def clean_downloads():
    for i in os.listdir('downloads'):
        os.remove('downloads/{}'.format(i))
    return redirect('/monitor')


def get_dm_name_list():
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
