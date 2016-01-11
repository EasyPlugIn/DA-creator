from flask import Flask
from flask import render_template
from flask import abort, redirect, url_for, request
from flask import send_from_directory
from jinja2 import Environment, FileSystemLoader

import subprocess as sub
import threading
import time
import os
import shutil
import urllib
import json


DOWNLOAD_PATH = 'downloads'

class DAProject:
    DA_TEMPLATE_ROOT_PATH = 'da-template'
    WORKSPACE_PATH = 'workspace'
    CUSTOM_CODE_PATH = 'custom'
    BACKUP_POSTFIX = 'backup'

    def __init__(self, dm_name):
        self.dm_name = dm_name.replace('-', '_')

    def create(self):
        self.clean()
        shutil.copytree(DAProject.DA_TEMPLATE_ROOT_PATH, self.root_path)
        shutil.move(
            os.path.join(self.root_path, 'MODEL'),
            os.path.join(self.root_path, self.dm_name)
        )
        shutil.move(
            os.path.join(self.root_path, self.dm_name, 'src/com/example/model'),
            os.path.join(self.root_path, self.dm_name, 'src/com/example/', self.dm_name.lower()),
        )

    def clean(self):
        shutil.rmtree(self.root_path, ignore_errors=True)

    @property
    def root_path(self):
        return os.path.join(DAProject.WORKSPACE_PATH, self.dm_name)

    @property
    def src_code_path(self):
        return os.path.join(
            self.root_path,
            self.dm_name,
            'src/com/example',
            self.dm_name.lower()
        )

    def custom_code_path(self, func=None):
        if func:
            return os.path.join(DAProject.CUSTOM_CODE_PATH, self.dm_name, func)
        return os.path.join(DAProject.CUSTOM_CODE_PATH, self.dm_name)

    def read_custom_code(self, func):
        return open(self.custom_code_path(func)).read()

    def write_custom_code(self, func, code):
        with open(self.custom_code_path(func), 'w') as f:
            f.write(code)

    def backup_custom_codes(self):
        shutil.rmtree(
            '{}.{}'.format(self.custom_code_path(), DAProject.BACKUP_POSTFIX),
            ignore_errors=True
        )
        shutil.copytree(
            self.custom_code_path(),
            '{}.{}'.format(self.custom_code_path(), DAProject.BACKUP_POSTFIX)
        )

    def cleanup_backuped_custom_codes(self):
        shutil.rmtree(
            '{}.{}'.format(self.custom_code_path(), DAProject.BACKUP_POSTFIX),
            ignore_errors=True
        )

    def recover_custom_codes(self):
        shutil.rmtree(self.custom_code_path())
        shutil.copytree(
            '{}.{}'.format(self.custom_code_path(), DAProject.BACKUP_POSTFIX),
            self.custom_code_path()
        )

    def update_custom_codes(self, custom_codes):
        self.write_custom_code('device2EasyConnect', custom_codes['device2Easyconnect'])
        self.write_custom_code('easyConnect2Device', custom_codes['easyConnect2Device'])

    def initialize_custom_codes(self):
        if not os.path.isdir(self.custom_code_path()):
            # editing this device model first time, create empty custom code
            os.mkdir(self.custom_code_path())
            self.write_custom_code('device2EasyConnect', '')
            self.write_custom_code('easyConnect2Device', '')

    def inject_custom_code(self, idf_list, odf_list):
        loader = FileSystemLoader(self.src_code_path)
        env = Environment(loader=loader)

        # get and render template file
        template = env.get_template('Custom.java')
        context = {
            'code_device2Easyconnect':  self.read_custom_code('device2EasyConnect'),
            'code_easyConnect2Device':  self.read_custom_code('easyConnect2Device'),
            'dm_name':                  self.dm_name,
            'dm_name_l':                self.dm_name.lower(),
            'idf_list':                 idf_list,
            'odf_list':                 odf_list,
        }
        custom_code = template.render(**context)

        with open(join(self.src_code_path, 'Custom.java'), 'w') as f:
            f.write(custom_code)

    def render_simple_files(self, filepath, filelist):
        if isinstance(filelist, str):
            filelist = [filelist]

        loader = FileSystemLoader(filepath)
        env = Environment(loader=loader)
        for filename in filelist:
            template = env.get_template(filename)
            result = template.render(
                dm_name=self.dm_name,
                dm_name_l=self.dm_name.lower(),
            )
            with open(os.path.join(filepath, filename), 'w') as f:
                f.write(result)

    def inject_package_name(self):
        #######################
        # AndroidManifest.xml #
        #######################
        self.render_simple_files(os.path.join(self.root_path, self.dm_name), 'AndroidManifest.xml')

        ###########################
        # res/layout & res/values #
        ###########################
        self.render_simple_files(
            os.path.join(self.root_path, self.dm_name, 'res/layout'),
            ('activity_main_connected.xml', 'activity_main_searching.xml')
        )

        self.render_simple_files(
            os.path.join(self.root_path, self.dm_name, 'res/values'),
            'strings.xml'
        )

        ################
        # source codes #
        ################
        self.render_simple_files(
            self.src_code_path,
            filter(lambda x: x.endswith('.java'), os.listdir(self.src_code_path))
        )

        ##########
        # Others #
        ##########
        self.render_simple_files(
            os.path.join(self.root_path, self.dm_name),
            '.project'
        )

        self.render_simple_files(
            self.root_path,
            'settings.gradle'
        )

    def build(self):
        p = sub.Popen(
            ['sh', 'workspace/compile.sh', self.dm_name],
            stdout=sub.PIPE,
            stderr=sub.PIPE,
            bufsize=1)

        while p.poll() == None:
            data = p.stdout.readline()
            compile_message[self.dm_name].append(str(data, 'utf-8'))

        if p.poll():
            compile_message[self.dm_name].extend(map(lambda x: str(x, 'utf-8'), p.stderr.readlines()))

        return 'S' if p.poll() == 0 else 'F'

    def update_apk(self):
        shutil.copyfile(
            self.build_apk_path,
            os.path.join(DOWNLOAD_PATH, self.dm_name + '.apk')
        )

    @property
    def build_apk_path(self):
        return os.path.join(
            self.root_path,
            self.dm_name,
            'build/outputs/apk',
            self.dm_name + '-debug.apk'
        )

    @property
    def apk_path(self):
        return os.path.join(DOWNLOAD_PATH, self.dm_name + '.apk')

app = Flask(__name__)
lock = threading.Semaphore()
thread_pool = {}
compile_message = {}    # compile message buffer
compile_result = {}     # compile result (status code)


def join(*args, **kwargs):
    return os.path.join(*args, **kwargs)


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
        custom_codes['device2Easyconnect'] = request.form['device2Easyconnect']
        custom_codes['easyConnect2Device'] = request.form['easyConnect2Device']
        custom_codes['idf_list'] = request.form.getlist('idf_list[]')
        custom_codes['odf_list'] = request.form.getlist('odf_list[]')
        t = threading.Thread(target=worker, args=(dm_name, custom_codes))
        t.daemon = True
        thread_pool[dm_name] = t
        lock.release()
        t.start()

    return render_template(template, **context)


def get_dm_name_list(): # {{{
    import urllib
    import json
    raw_data = json.loads(str(
            urllib.request.urlopen(
                'http://openmtc.darkgerm.com:7788/get_model_list',
                bytes('', 'utf8')
            ).readall(), 'utf8'
        ))
    return raw_data
#}}}


def get_df_list(dm_name):   #{{{
    raw_data = json.loads(str(
            urllib.request.urlopen(
                'http://openmtc.darkgerm.com:7788/get_model_info_for_da',
                bytes('model_name={}'.format(dm_name), 'utf8')
            ).readall(), 'utf8'
        ))
    return {'idf': map(lambda x: x[0], raw_data['idf']), 'odf': map(lambda x: x[0], raw_data['odf'])}
#}}}


def da_creator_form(dm_name):
    template = 'da-creator-form.html'

    da_project = DAProject(dm_name)
    da_project.initialize_custom_codes()
    df_list = get_df_list(dm_name)

    context = {
        'dm_name': dm_name,
        'idf_list': df_list['idf'],
        'odf_list': df_list['odf'],
        'dm_name_list': get_dm_name_list(),
        'da_available': os.path.exists(da_project.apk_path),

        'code_device2Easyconnect': da_project.read_custom_code('device2EasyConnect'),
        'code_easyConnect2Device': da_project.read_custom_code('easyConnect2Device'),

        'email': 'pi314.cs03g@nctu.edu.tw',
    }
    return render_template(template, **context)


def worker(dm_name, custom_codes):
    global compile_message
    global compile_result

    compile_message[dm_name] = []
    compile_result[dm_name] = 'C'     # 'C'ompiling  'S'uccess  'F'ailed

    print(dm_name, 'worker')
    da_project = DAProject(dm_name)
    da_project.backup_custom_codes()
    da_project.update_custom_codes(custom_codes)
    da_project.create()
    da_project.inject_custom_code(custom_codes['idf_list'], custom_codes['odf_list'])
    da_project.inject_package_name()
    compile_result[dm_name] = da_project.build()
    if compile_result[dm_name] == 'S':
        da_project.cleanup_backuped_custom_codes()
        da_project.update_apk()
    else:
        da_project.recover_custom_codes()

    da_project.clean()
    print(dm_name, 'end')


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


if __name__ == '__main__':
    # app.run(host='0.0.0.0', port=8000, debug=False)
    app.run(host='0.0.0.0', port=8000, debug=True)
