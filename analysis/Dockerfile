FROM python:3.8

# RUN groupadd -r user -g 1000 && useradd --no-log-init -r -g user -u 1000 user

# USER 1000

# WORKDIR /home/user/

RUN pip install --no-cache pandas dask matplotlib seaborn tqdm pyarrow

WORKDIR /workdir

ADD . analyzer/

EXPOSE 8888
